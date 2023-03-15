package org.kauai;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;

public class KCommandContinuousRead implements KCommand {
    private Thread thread;
    private final String podName;
    private final Kauai mainApp;
    Process proc;

    public KCommandContinuousRead(String podName, Kauai mainApp) {
        this.podName = podName;
        this.mainApp = mainApp;
        createPanel();
    }

    public void shutdown() {
        this.proc.destroy();
        this.thread.interrupt();
        this.thread = null;
    }

    private void logs(String podName, PrintStream printStream) {
        thread = new Thread(() -> {
            try {
                String[] commands = {"kubectl", "logs", "--follow", podName};
                ProcessBuilder pb = new ProcessBuilder(commands);
                pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
                proc = pb.start();

                BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

                String s = null;
                while ((s = stdInput.readLine()) != null) {
                    printStream.println(s);
                    Thread.sleep(1);
                }
                while ((s = stdError.readLine()) != null) {
                    System.out.println("ERROR: " + s);
                }
            } catch (Exception e) {
                System.out.println(e);
            }
        });

        thread.setName("Logs for " + podName);
        thread.start();
    }

    private void createPanel() {
        JTextArea ta = new JTextArea();
        ta.setEditable(false);
        Font font = new Font("Courier New", Font.PLAIN, 15);
        ta.setFont(font);
        TextAreaOutputStream taos = new TextAreaOutputStream(ta, 60);
        PrintStream ps = new PrintStream( taos );
        logs(podName, ps);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(new JScrollPane(ta));
        panel.setVisible(true);
        mainApp.newPanel(panel, new KAction("pod", podName, KAction.Type.LOGS));
    }
}
