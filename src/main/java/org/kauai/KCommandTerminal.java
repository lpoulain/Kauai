package org.kauai;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

import static org.kauai.KAction.Type.SHELL;

public class KCommandTerminal implements KCommand {
    ConsolePane panel;
    JFrame frame;
    String pod;
    String namespace;
    Kauai mainApp;

    public KCommandTerminal(String pod, String namespace, Kauai mainApp) {
        this.pod = pod;
        this.namespace = namespace;
        this.mainApp = mainApp;

        EventQueue.invokeLater(() -> {
            panel = new ConsolePane(pod);
            panel.setVisible(true);
            mainApp.newPanel(panel, new KAction("pod", pod, SHELL, namespace));
        });
    }

    public void shutdown() {
        panel.cmd.runner.process.destroy();
        panel.cmd.runner.interrupt();
    }

    public interface CommandListener {
        void commandOutput(String text);
        void commandCompleted(String cmd, int result);
        void commandFailed(Exception exp);
    }

    public class ConsolePane extends JPanel implements CommandListener, Terminal {
        private JTextArea textArea;
        private int userInputStart = 0;
        private Command cmd;
        private final String pod;

        public ConsolePane(String pod) {
            this.pod = pod;

            cmd = new Command(this);
            cmd.runner = new ProcessRunner(cmd.listener, null, pod, namespace);
            cmd.runner.setName("Shell for " + pod);

            setLayout(new BorderLayout());
            textArea = new JTextArea(30, 80);
            Font font = new Font("Courier New", Font.PLAIN, 15);
            textArea.setFont(font);
            ((AbstractDocument) textArea.getDocument()).setDocumentFilter(new ProtectedDocumentFilter(this));
            add(new JScrollPane(textArea));

            InputMap im = textArea.getInputMap(WHEN_FOCUSED);
            ActionMap am = textArea.getActionMap();

            Action oldAction = am.get("insert-break");
            am.put("insert-break", new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int range = textArea.getCaretPosition() - userInputStart;
                    try {
                        String text = textArea.getText(userInputStart, range).trim();
                        userInputStart += range;
                        if (!cmd.isRunning()) {
                            cmd.execute(text);
                        } else {
                            try {
                                cmd.send(text + "\n");
                            } catch (IOException ex) {
                                appendText("!! Failed to send command to process: " + ex.getMessage() + "\n");
                            }
                        }
                    } catch (BadLocationException ex) {
                        Logger.getLogger(KCommandTerminal.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    oldAction.actionPerformed(e);
                }
            });
        }

        @Override
        public void commandOutput(String text) {
            SwingUtilities.invokeLater(new AppendTask(this, text));
        }

        @Override
        public void commandFailed(Exception exp) {
            SwingUtilities.invokeLater(new AppendTask(this, "Command failed - " + exp.getMessage()));
        }

        @Override
        public void commandCompleted(String cmd, int result) {
            appendText("\n> " + cmd + " exited with " + result + "\n");
            appendText("\n");
        }

        protected void updateUserInputPos() {
            int pos = textArea.getCaretPosition();
            textArea.setCaretPosition(textArea.getText().length());
            userInputStart = pos;

        }

        @Override
        public int getUserInputStart() {
            return userInputStart;
        }

        @Override
        public void appendText(String text) {
            textArea.append(text);
            updateUserInputPos();
        }
    }

    public interface UserInput {

        public int getUserInputStart();
    }

    public interface Terminal extends UserInput {
        public void appendText(String text);
    }

    public class AppendTask implements Runnable {
        private Terminal terminal;
        private String text;

        public AppendTask(Terminal textArea, String text) {
            this.terminal = textArea;
            this.text = text;
        }

        @Override
        public void run() {
            terminal.appendText(text);
        }
    }

    public class Command {
        private CommandListener listener;
        private ProcessRunner runner;

        public Command(CommandListener listener) {
            this.listener = listener;
        }

        public boolean isRunning() {
            return runner != null && runner.isAlive();
        }

        public void execute(String cmd) {
            if (!cmd.trim().isEmpty()) {
                List<String> values = new ArrayList<>(25);
                if (cmd.contains("\"")) {
                    while (cmd.contains("\"")) {
                        String start = cmd.substring(0, cmd.indexOf("\""));
                        cmd = cmd.substring(start.length());
                        String quote = cmd.substring(cmd.indexOf("\"") + 1);
                        cmd = cmd.substring(cmd.indexOf("\"") + 1);
                        quote = quote.substring(0, cmd.indexOf("\""));
                        cmd = cmd.substring(cmd.indexOf("\"") + 1);

                        if (!start.trim().isEmpty()) {
                            String parts[] = start.trim().split(" ");
                            values.addAll(Arrays.asList(parts));
                        }
                        values.add(quote.trim());
                    }

                    if (!cmd.trim().isEmpty()) {
                        String parts[] = cmd.trim().split(" ");
                        values.addAll(Arrays.asList(parts));
                    }
                } else {
                    if (!cmd.trim().isEmpty()) {
                        String parts[] = cmd.trim().split(" ");
                        values.addAll(Arrays.asList(parts));
                    }
                }

                runner = new ProcessRunner(listener, values, pod, namespace);
            }
        }

        public void send(String cmd) throws IOException {
            runner.write(cmd);
        }
    }

    public class ProcessRunner extends Thread {
        private final List<String> cmds;
        private final CommandListener listener;
        private final String pod;
        private final String namespace;

        private Process process;

        public ProcessRunner(CommandListener listener, List<String> cmds, String pod, String namespace) {
            this.pod = pod;
            this.namespace = namespace;
            this.cmds = cmds;
            this.listener = listener;
            start();
        }

        @Override
        public void run() {
            try {
                String[] commands = {"kubectl", "exec", "--stdin", pod, "--", "/bin/ash", "--namespace=" + namespace};
                ProcessBuilder pb = new ProcessBuilder(commands);
                pb.redirectErrorStream();
                System.out.println(String.join(" ", commands));
                process = pb.start();
                StreamReader reader = new StreamReader(listener, process.getInputStream());
                StreamReader errorReader = new StreamReader(listener, process.getErrorStream());

                process.waitFor();

                // Terminate the stream writer
                reader.join();
                errorReader.join();
            } catch (Exception exp) {
                exp.printStackTrace();
                listener.commandFailed(exp);
            }
        }

        public void write(String text) throws IOException {
            if (process != null && process.isAlive()) {
                process.getOutputStream().write(text.getBytes());
                process.getOutputStream().flush();
            }
        }
    }

    public class StreamReader extends Thread {
        private InputStream is;
        private CommandListener listener;

        public StreamReader(CommandListener listener, InputStream is) {
            this.is = is;
            this.listener = listener;
            start();
        }

        @Override
        public void run() {
            try {
                int value = -1;
                while ((value = is.read()) != -1) {
                    listener.commandOutput(Character.toString((char) value));
                }
            } catch (IOException exp) {
                exp.printStackTrace();
            }
        }
    }

    public class ProtectedDocumentFilter extends DocumentFilter {
        private UserInput userInput;
        public ProtectedDocumentFilter(UserInput userInput) {
            this.userInput = userInput;
        }
        public UserInput getUserInput() {
            return userInput;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            if (offset >= getUserInput().getUserInputStart()) {
                super.insertString(fb, offset, string, attr);
            }
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
            if (offset >= getUserInput().getUserInputStart()) {
                super.remove(fb, offset, length); //To change body of generated methods, choose Tools | Templates.
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            if (offset >= getUserInput().getUserInputStart()) {
                super.replace(fb, offset, length, text, attrs); //To change body of generated methods, choose Tools | Templates.
            }
        }
    }
}
