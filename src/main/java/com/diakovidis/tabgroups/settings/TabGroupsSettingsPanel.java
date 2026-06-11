package com.diakovidis.tabgroups.settings;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.diakovidis.tabgroups.model.TabGroup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Swing panel for editing Tab Groups settings.
 * <p>
 * Left: three-column table (✓ enabled | Order | Name) sorted by order.
 * Right: detail fields — name, order ≥ 0, regex + validate button.
 * Toolbar: Load Preset, Duplicate, Export, Import.
 */
public class TabGroupsSettingsPanel {

    // ── Preset registry ───────────────────────────────────────────────────────
    private static final Map<String, String> PRESETS = new LinkedHashMap<>();
    static {
        PRESETS.put("Java / Maven",                "/presets/java-maven.json");
        PRESETS.put("Java / Spring Boot",          "/presets/java-spring-boot.json");
        PRESETS.put("Java + Angular (Full-stack)", "/presets/java-web.json");
        PRESETS.put("Angular / TypeScript",        "/presets/angular.json");
        PRESETS.put("Python / Django",             "/presets/python-django.json");
        PRESETS.put("PHP / Laravel",               "/presets/php-laravel.json");
        PRESETS.put("Go",                          "/presets/go.json");
        PRESETS.put("Kotlin / Android",            "/presets/kotlin-android.json");
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    private final JPanel mainPanel;
    private final DefaultTableModel tableModel;
    private final JBTable groupTable;
    private final JTextField nameField;
    private final JSpinner   orderSpinner;
    private final JTextField regexField;

    private final List<TabGroup> tabGroups = new ArrayList<>();
    private int     currentIndex = -1;
    private boolean isUpdating   = false;

    public TabGroupsSettingsPanel() {
        mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // col 0 = Boolean (enabled checkbox), col 1 = Integer (order), col 2 = String (name)
        tableModel = new DefaultTableModel(new Object[]{"✓", "Order", "Name"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return col == 0; }
            @Override public Class<?> getColumnClass(int col) {
                return switch (col) { case 0 -> Boolean.class; case 1 -> Integer.class; default -> String.class; };
            }
        };

        groupTable = new JBTable(tableModel);
        groupTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        groupTable.setShowGrid(false);
        groupTable.setRowSelectionAllowed(true);
        groupTable.getTableHeader().setReorderingAllowed(false);
        groupTable.getColumnModel().getColumn(0).setPreferredWidth(28);
        groupTable.getColumnModel().getColumn(0).setMaxWidth(28);
        groupTable.getColumnModel().getColumn(1).setPreferredWidth(50);
        groupTable.getColumnModel().getColumn(1).setMaxWidth(65);
        groupTable.getColumnModel().getColumn(2).setPreferredWidth(142);

        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(groupTable)
                .setAddAction(new AnActionButtonRunnable() {
                    @Override public void run(AnActionButton b) { addGroup(); }
                })
                .setRemoveAction(new AnActionButtonRunnable() {
                    @Override public void run(AnActionButton b) { removeGroup(); }
                })
                .setMoveUpAction(new AnActionButtonRunnable() {
                    @Override public void run(AnActionButton b) { moveGroup(-1); }
                })
                .setMoveDownAction(new AnActionButtonRunnable() {
                    @Override public void run(AnActionButton b) { moveGroup(1); }
                })
                .addExtraAction(new LoadPresetAction())
                .addExtraAction(new DuplicateGroupAction())
                .addExtraAction(new ExportGroupsAction())
                .addExtraAction(new ImportGroupsAction());

        JPanel leftPanel = decorator.createPanel();
        leftPanel.setBorder(new TitledBorder("Tab Groups"));
        leftPanel.setPreferredSize(new Dimension(240, 0));

        // ── Right detail panel ─────────────────────────────────────────────────
        JPanel rightPanel = new JPanel(new BorderLayout(5, 10));
        rightPanel.setBorder(new TitledBorder("Group Details"));

        JPanel detailPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        detailPanel.add(new JLabel("Name:"), gbc);
        nameField = new JTextField(20);
        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        detailPanel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        detailPanel.add(new JLabel("Order:"), gbc);
        orderSpinner = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
        gbc.gridx = 1; gbc.gridy = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        detailPanel.add(orderSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        detailPanel.add(new JLabel("Regex:"), gbc);
        regexField = new JTextField(30);
        gbc.gridx = 1; gbc.gridy = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        detailPanel.add(regexField, gbc);

        JButton validateBtn = new JButton("Validate");
        gbc.gridx = 1; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; gbc.anchor = GridBagConstraints.WEST;
        detailPanel.add(validateBtn, gbc);


        rightPanel.add(detailPanel, BorderLayout.NORTH);

        mainPanel.add(leftPanel,  BorderLayout.WEST);
        mainPanel.add(rightPanel, BorderLayout.CENTER);

        // ── Listeners ──────────────────────────────────────────────────────────
        groupTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || isUpdating) return;
            saveCurrentGroupDetails();
            loadGroupDetails(groupTable.getSelectedRow());
        });

        // Checkbox column edited directly in table → sync to model
        tableModel.addTableModelListener(new TableModelListener() {
            @Override public void tableChanged(TableModelEvent e) {
                if (isUpdating) return;
                if (e.getColumn() == 0 && e.getFirstRow() >= 0 && e.getFirstRow() < tabGroups.size()) {
                    tabGroups.get(e.getFirstRow()).setEnabled((Boolean) tableModel.getValueAt(e.getFirstRow(), 0));
                }
            }
        });

        validateBtn.addActionListener(e -> validateRegex());
        nameField.addActionListener(e -> { if (!isUpdating) saveCurrentGroupDetails(); });
        orderSpinner.addChangeListener(e -> { if (!isUpdating) saveCurrentGroupDetails(); });
        regexField.addActionListener(e -> { if (!isUpdating) saveCurrentGroupDetails(); });


        setDetailsEnabled(false);
    }

    // ── Mutators ──────────────────────────────────────────────────────────────

    private void addGroup() {
        saveCurrentGroupDetails();
        int nextOrder = tabGroups.stream().mapToInt(TabGroup::getOrder).max().orElse(-1) + 1;
        tabGroups.add(new TabGroup("New Group", nextOrder, ""));
        refreshTableAndSelect(tabGroups.size() - 1);
    }

    private void removeGroup() {
        int idx = groupTable.getSelectedRow();
        if (idx >= 0 && idx < tabGroups.size()) {
            tabGroups.remove(idx);
            currentIndex = -1;
            refreshTableAndSelect(tabGroups.isEmpty() ? -1 : Math.min(idx, tabGroups.size() - 1));
        }
    }

    private void moveGroup(int delta) {
        int idx    = groupTable.getSelectedRow();
        int target = idx + delta;
        if (idx < 0 || target < 0 || target >= tabGroups.size()) return;
        saveCurrentGroupDetails();
        TabGroup tmp = tabGroups.get(idx);
        tabGroups.set(idx, tabGroups.get(target));
        tabGroups.set(target, tmp);
        int oi = tabGroups.get(idx).getOrder(), ot = tabGroups.get(target).getOrder();
        tabGroups.get(idx).setOrder(ot);
        tabGroups.get(target).setOrder(oi);
        refreshTableAndSelect(target);
    }

    private void duplicateGroup() {
        int idx = groupTable.getSelectedRow();
        if (idx >= 0 && idx < tabGroups.size()) {
            saveCurrentGroupDetails();
            TabGroup dup = tabGroups.get(idx).copy();
            dup.setName(tabGroups.get(idx).getName() + " (copy)");
            dup.setOrder(tabGroups.stream().mapToInt(TabGroup::getOrder).max().orElse(-1) + 1);
            tabGroups.add(dup);
            refreshTableAndSelect(tabGroups.size() - 1);
        }
    }

    private void loadPreset(String resourcePath) {
        if (!tabGroups.isEmpty()) {
            int confirm = JOptionPane.showConfirmDialog(mainPanel,
                    "<html><b>Loading a preset will replace your current Tab Groups configuration.</b><br><br>" +
                    "All existing groups will be removed. This cannot be undone.<br><br>" +
                    "Do you want to continue?</html>",
                    "Confirm Load Preset", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
        }
        try {
            List<TabGroup> preset = TabGroupsPorter.loadPreset(resourcePath);
            if (preset == null) {
                JOptionPane.showMessageDialog(mainPanel, "Preset not found: " + resourcePath,
                        "Load Preset Failed", JOptionPane.ERROR_MESSAGE);
                return;
            }
            setTabGroups(preset);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainPanel, "Failed to load preset:\n" + ex.getMessage(),
                    "Load Preset Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public JPanel getPanel() { return mainPanel; }

    public List<TabGroup> getTabGroups() {
        saveCurrentGroupDetails();
        List<TabGroup> result = new ArrayList<>();
        for (TabGroup g : tabGroups) result.add(g.copy());
        return result;
    }

    public void setTabGroups(List<TabGroup> groups) {
        currentIndex = -1;
        tabGroups.clear();
        for (TabGroup g : groups) tabGroups.add(g.copy());
        refreshTableAndSelect(tabGroups.isEmpty() ? -1 : 0);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void refreshTableAndSelect(int selectIndex) {
        tabGroups.sort(Comparator.comparingInt(TabGroup::getOrder));
        isUpdating = true;
        try {
            tableModel.setRowCount(0);
            for (TabGroup g : tabGroups) {
                tableModel.addRow(new Object[]{
                    g.isEnabled(),
                    g.getOrder(),
                    g.getName().isEmpty() ? "<unnamed>" : g.getName()
                });
            }
            if (selectIndex >= 0 && selectIndex < tabGroups.size()) {
                groupTable.setRowSelectionInterval(selectIndex, selectIndex);
                groupTable.scrollRectToVisible(groupTable.getCellRect(selectIndex, 0, true));
            } else {
                groupTable.clearSelection();
            }
        } finally {
            isUpdating = false;
        }
        loadGroupDetails(selectIndex);
    }

    private void saveCurrentGroupDetails() {
        if (currentIndex >= 0 && currentIndex < tabGroups.size()) {
            TabGroup g = tabGroups.get(currentIndex);
            g.setName(nameField.getText().trim());
            g.setOrder((Integer) orderSpinner.getValue());
            g.setRegex(regexField.getText().trim());
            if (currentIndex < tableModel.getRowCount()) {
                isUpdating = true;
                try {
                    tableModel.setValueAt(g.isEnabled(), currentIndex, 0);
                    tableModel.setValueAt(g.getOrder(),  currentIndex, 1);
                    tableModel.setValueAt(g.getName().isEmpty() ? "<unnamed>" : g.getName(), currentIndex, 2);
                } finally {
                    isUpdating = false;
                }
            }
        }
    }

    private void loadGroupDetails(int index) {
        isUpdating = true;
        try {
            currentIndex = index;
            if (index >= 0 && index < tabGroups.size()) {
                TabGroup g = tabGroups.get(index);
                nameField.setText(g.getName());
                orderSpinner.setValue(g.getOrder());
                regexField.setText(g.getRegex());
                setDetailsEnabled(true);
            } else {
                nameField.setText("");
                orderSpinner.setValue(0);
                regexField.setText("");
                setDetailsEnabled(false);
            }
        } finally {
            isUpdating = false;
        }
    }

    private void setDetailsEnabled(boolean enabled) {
        nameField.setEnabled(enabled);
        orderSpinner.setEnabled(enabled);
        regexField.setEnabled(enabled);
    }

    private void validateRegex() {
        String regex = regexField.getText().trim();
        if (regex.isEmpty()) {
            JOptionPane.showMessageDialog(mainPanel, "Regex is empty.", "Validation", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            Pattern.compile(regex);
            JOptionPane.showMessageDialog(mainPanel, "Regex is valid.", "Validation", JOptionPane.INFORMATION_MESSAGE);
        } catch (PatternSyntaxException ex) {
            JOptionPane.showMessageDialog(mainPanel, "Invalid regex: " + ex.getDescription(), "Validation Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Inner actions ─────────────────────────────────────────────────────────

    private class LoadPresetAction extends AnAction {
        LoadPresetAction() {
            super("Load Preset", "Replace current groups with a built-in preset", AllIcons.Actions.RunAll);
        }
        @Override public void actionPerformed(@NotNull AnActionEvent e) {
            String[] names = PRESETS.keySet().toArray(new String[0]);
            String choice = (String) JOptionPane.showInputDialog(
                    mainPanel, "Choose a preset:", "Load Preset",
                    JOptionPane.PLAIN_MESSAGE, null, names, names[0]);
            if (choice != null) loadPreset(PRESETS.get(choice));
        }
        @Override public void update(@NotNull AnActionEvent e) { e.getPresentation().setEnabled(true); }
    }

    private class DuplicateGroupAction extends AnAction {
        DuplicateGroupAction() { super("Duplicate", "Duplicate selected group", AllIcons.Actions.Copy); }
        @Override public void actionPerformed(@NotNull AnActionEvent e) { duplicateGroup(); }
        @Override public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(groupTable.getSelectedRow() >= 0);
        }
    }

    private class ExportGroupsAction extends AnAction {
        ExportGroupsAction() { super("Export", "Export groups to JSON", AllIcons.Actions.Upload); }
        @Override public void actionPerformed(@NotNull AnActionEvent e) {
            saveCurrentGroupDetails();
            TabGroupsPorter.export(new ArrayList<>(tabGroups), mainPanel);
        }
        @Override public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(!tabGroups.isEmpty());
        }
    }

    private class ImportGroupsAction extends AnAction {
        ImportGroupsAction() { super("Import", "Import groups from JSON", AllIcons.Actions.Download); }
        @Override public void actionPerformed(@NotNull AnActionEvent e) {
            List<TabGroup> imported = TabGroupsPorter.importGroups(mainPanel);
            if (imported != null) setTabGroups(imported);
        }
        @Override public void update(@NotNull AnActionEvent e) { e.getPresentation().setEnabled(true); }
    }
}
