package org.kauai;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.HashMap;
import java.util.Map;

public class Kauai {
    private KObjectTable pods;
    private JFrame frame;
    private JPanel p1;
    private JPanel p2;
    private JSplitPane splitPane;
    private JTabbedPane tabbedPane;
    Map<String, KCommand> kcommands = new HashMap<>();

    public static void main(String[] args) throws Exception {
        new Kauai();
    }

    public Kauai() throws Exception {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (UnsupportedLookAndFeelException ex) {
            System.out.println("Error");
        }

        frame = new JFrame("frame");
        frame.setTitle("Kauai");
        frame.setSize(2000,1200);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                for (KCommand cmd : kcommands.values()) {
                    cmd.shutdown();
                }
                System.exit(0);
            }
        });

        Kubectl k = new Kubectl();
        pods = new KObjectTable(k, "pod", this);
        p1 = pods.getPanel();
        p2 = new JPanel();
        p2 = new JPanel();
        tabbedPane = new JTabbedPane();
        p2.setLayout(new BorderLayout());
        p2.add(tabbedPane);

        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, p1, p2);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(150);
        frame.add(splitPane);
        frame.setVisible(true);
    }

    public void createTab(KAction action) {
        String actionDesc = action.getDescription();
        int tabIndex = getTabIndex(actionDesc);
        if (tabIndex >= 0){
            tabbedPane.setSelectedIndex(tabIndex);
            return;
        }
        KCommand cmd = null;
        switch (action.getType()) {
            case SHELL -> cmd = new KCommandTerminal(action.getResourceName(), this);
            case LOGS -> cmd = new KCommandContinuousRead(action.getResourceName(), this);
            case DESCRIBE -> cmd = new KCommandReadOutput(action, this);
        }
        kcommands.put(actionDesc, cmd);
    }

    private int getTabIndex(String resourceDesc) {
        for (int i=0; i<tabbedPane.getTabCount(); i++) {
            if (resourceDesc.equals(tabbedPane.getToolTipTextAt(i))) {
                return i;
            }
        }

        return -1;
    }

    public void newPanel(JPanel panel, KAction action) {
        String actionDescription = action.getDescription();
        int divider = splitPane.getDividerLocation();
        tabbedPane.addTab(action.getResourceName(), null, panel, actionDescription);
        tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 1);
        int index = tabbedPane.getTabCount() - 1;

        JPanel pnlTab = new JPanel(new GridBagLayout());
        pnlTab.setOpaque(false);
        JLabel lblTitle = new JLabel(action.getResourceName() + " ");
        switch (action.getType()) {
            case SHELL -> lblTitle.setForeground(Color.RED);
            case LOGS -> lblTitle.setForeground(Color.GREEN);
            case DESCRIBE -> lblTitle.setForeground(Color.YELLOW);
        }
        JButton btnClose = new JButton("x");
        btnClose.addActionListener(e -> {
            tabbedPane.remove(panel);
            kcommands.remove(actionDescription).shutdown();
        });

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;

        pnlTab.add(lblTitle, gbc);

        gbc.gridx++;
        gbc.weightx = 0;
        pnlTab.add(btnClose, gbc);

        tabbedPane.setTabComponentAt(index, pnlTab);
        splitPane.setDividerLocation(divider);
    }
}
