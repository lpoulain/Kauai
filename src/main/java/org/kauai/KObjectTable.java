package org.kauai;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

import static org.kauai.KAction.Type.DESCRIBE;
import static org.kauai.KAction.Type.LOGS;
import static org.kauai.KAction.Type.SHELL;

public class KObjectTable {
    private final Kubectl k;
    private String kobject;
    private KObjectMetadata metadata;
    List<KObject> items;
    private final JPanel panel;
    private final Kauai mainApp;
    private String filter = "";
    private JTable table;
    private JScrollPane scrollPane;
    private JLabel filterLabel;
    private TableRowSorter<TableModel> sorter;
    private GridLayout layout;

    JPanel getPanel() {
        return this.panel;
    }

    KObjectTable(Kubectl k, String kobject, Kauai mainApp) throws Exception {
        this.k = k;
        panel = new JPanel();
//        panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Pods", TitledBorder.CENTER, TitledBorder.TOP));
        panel.setLayout(new BorderLayout());
        this.kobject = kobject;
        this.items = k.get(kobject);
        this.mainApp = mainApp;
        this.metadata = k.getMetadata(kobject);

        layout = new GridLayout(1, 4);
        layout.setHgap(10);
        JPanel topPanel2 = new JPanel(new GridBagLayout());
        JPanel topPanel = new JPanel(layout);
//        topPanel.setLayout(new BorderLayout());

        JRadioButton radioService = new JRadioButton("Services");
        radioService.addActionListener((e) -> changeType("service"));

        JRadioButton radioDeployment = new JRadioButton("Deployments");
        radioDeployment.addActionListener((e) -> changeType("deployment"));

        JRadioButton radioPods = new JRadioButton("Pods");
        radioPods.addActionListener((e) -> changeType("pod"));
        radioPods.setSelected(true);

        ButtonGroup group = new ButtonGroup();
        group.add(radioService);
        group.add(radioDeployment);
        group.add(radioPods);
        topPanel.add(radioService);
        topPanel.add(radioDeployment);
        topPanel.add(radioPods);

        filterLabel = new JLabel();
        filterLabel.setText("Filter: ");
        filterLabel.setVisible(true);
        topPanel.add(filterLabel);

        topPanel2.add(topPanel);
        panel.add(topPanel2, BorderLayout.NORTH);

        createTable();
        panel.revalidate();
        panel.repaint();
    }

    private void changeType(String kobject) {
        this.kobject = kobject;
        try {
            this.items = k.get(kobject);
        } catch (Exception e) {
            System.out.println(e);
            return;
        }
        this.filter = "";
        this.metadata = k.getMetadata(kobject);

        scrollPane.remove(table);
        panel.remove(scrollPane);

        createTable();
        panel.revalidate();
        panel.repaint();
        table.setRowSelectionInterval(0, 0);
        table.grabFocus();
    }

    private void createTable() {
        String[][] data = items
                .stream()
                .filter(item -> item.matches(filter))
                .map(KObject::getValues)
                .toArray(String[][]::new);
        String[] labels = metadata.getLabels();

        TableModel model = new DefaultTableModel(data, labels) {
            public Class getColumnClass(int column) {
                Class returnValue;
                if((column >= 0) && (column < getColumnCount())) {
                    returnValue = getValueAt(0, column).getClass();
                } else {
                    returnValue = Object.class;
                }
                return returnValue;
            }
        };

        table = new JTable(data, labels);
        table.setFont(new Font("Verdana", Font.PLAIN, 15));
        table.setRowHeight(20);
        table.setDefaultEditor(Object.class, null);
        sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        table.addKeyListener(new KObjectTableKeyAdapter(table));

        JTableHeader tableHeader = table.getTableHeader();
        Font headerFont = new Font("Verdana", Font.PLAIN, 14);
        tableHeader.setFont(headerFont);

        final JPopupMenu popupMenu = new JPopupMenu();
        if (metadata.supportsAction(SHELL)) {
            JMenuItem shellItem = new JMenuItem("Shell (^s)");
            shellItem.setForeground(Color.RED);
            shellItem.addActionListener((e) -> menuAction(SHELL));
            popupMenu.add(shellItem);
        }
        if (metadata.supportsAction(LOGS)) {
            JMenuItem logsItem = new JMenuItem("Logs (^l)");
            logsItem.addActionListener((e) -> menuAction(LOGS));
            logsItem.setForeground(Color.GREEN);
            popupMenu.add(logsItem);
        }
        if (metadata.supportsAction(DESCRIBE)) {
            JMenuItem describeItem = new JMenuItem("Describe (^d)");
            describeItem.addActionListener((e) -> menuAction(DESCRIBE));
            describeItem.setForeground(Color.YELLOW);
            popupMenu.add(describeItem);
        }

        table.setComponentPopupMenu(popupMenu);
        scrollPane = new JScrollPane(table);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.setVisible(true);
    }

    private void setTableFilter(String filter) {
        sorter.setRowFilter(RowFilter.regexFilter(filter));
        if (table.getSelectedRow() == -1 && table.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }
        filterLabel.setText("Filter: " + filter);
    }

    private void menuAction(KAction.Type actionType) {
        int index = table.getSelectedRow();
        if (index < 0 || index >= items.size()) {
            return;
        }
        KObject item = items.get(index);
        mainApp.createTab(new KAction(kobject, item.getName(), actionType));
    }

    class KObjectTableKeyAdapter extends KeyAdapter {
        private final JTable table;
        public KObjectTableKeyAdapter(JTable table) {
            this.table = table;
        }

        @Override
        public void keyPressed(KeyEvent e) {
            char newChar = e.getKeyChar();
            if ((e.getModifiersEx() & (KeyEvent.SHIFT_DOWN_MASK | KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK | KeyEvent.ALT_GRAPH_DOWN_MASK | KeyEvent.META_DOWN_MASK)) != 0) {
                int index = table.getSelectedRow();
                if (index < 0 || index >= items.size()) {
                    return;
                }
                KObject item = items.get(index);
                KAction.Type actionType = null;
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_S -> actionType = SHELL;
                    case KeyEvent.VK_L -> actionType = LOGS;
                    case KeyEvent.VK_D -> actionType = DESCRIBE;
                }

                if (actionType != null && metadata.supportsAction(actionType)) {
                    mainApp.createTab(new KAction(kobject, item.getName(), actionType));
                }

                return;
            }

            switch (newChar) {
                case '\b' -> {
                    if (!filter.isEmpty()) {
                        filter = filter.substring(0, filter.length() - 1);
                        setTableFilter(filter);
                    }
                }
                case '\n', '\t', '\uFFFF' -> {}
                default -> {
                    filter += newChar;
                    setTableFilter(filter);
                }
            }
        }
    }
}
