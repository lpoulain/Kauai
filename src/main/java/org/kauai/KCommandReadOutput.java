package org.kauai;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Font;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;


public class KCommandReadOutput implements KCommand {
    private final KAction action;
    private final Kauai mainApp;

    public KCommandReadOutput(KAction action, Kauai mainApp) {
        this.action = action;
        this.mainApp = mainApp;
        createPanel();
    }

    private void call(PrintStream printStream) {
        try {
            List<String> commands = new ArrayList<>();
            commands.add("kubectl");
            switch (action.getType()) {
                case DESCRIBE -> {
                    commands.add("describe");
                    commands.add(action.getResourceType());
                    commands.add(action.getResourceName());
                }
                case LOGS -> {
                    commands.add("logs");
                    commands.add(action.getResourceName());
                }
            }
            commands.add("--namespace=" + action.getNamespace());
            System.out.println(String.join(" ", commands));
            ProcessBuilder pb = new ProcessBuilder(commands);
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
            Process proc = pb.start();

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

            String s = null;
            while ((s = stdInput.readLine()) != null) {
                printStream.println(s);
            }
            while ((s = stdError.readLine()) != null) {
                System.out.println("ERROR: " + s);
            }
            proc.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createPanel() {
        JTextArea ta = new JTextArea();
        JScrollPane scroll = new JScrollPane(ta);
        ta.setEditable(false);
        Font font = new Font("Courier New", Font.PLAIN, 15);
        ta.setFont(font);
        TextAreaOutputStream taos = new TextAreaOutputStream(ta, 60);
        PrintStream ps = new PrintStream( taos );
        call(ps);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(scroll);
        panel.setVisible(true);
        mainApp.newPanel(panel, action);
    }

    @Override
    public void shutdown() {

    }
}
