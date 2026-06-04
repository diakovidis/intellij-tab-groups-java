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
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Swing panel for editing Tab Groups settings.
 * Left side: a two-column table (Order | Name) always sorted by order.
 * Right side: detail fields (name, order ≥ 0, regex).
 */
public class TabGroupsSettingsPanel {

    private final JPanel mainPanel;

    // Left side: group table
    private final DefaultTableModel tableModel;
    private final JBTable groupTable;

    // Right side: detail editing
    private final JTextField nameField;
    private final JSpinner orderSpinner;
    private final JTextField regexField;

    // Data — always kept sorted by order
    private final List<TabGroup> tabGroups = new ArrayList<>();
    private int currentIndex = -1;

    /** Guard flag to suppress selection listener during programmatic table updates. */
    private boolean isUpdating = false;

    public TabGroupsSettingsPanel() {
        mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ===== Left panel: group table =====
        tableModel = new DefaultTableModel(new Object[]{"Order", "Name"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
            @Override public Class<?> getColumnClass(int col) {
                return col == 0 ? Integer.class : String.class;
            }
        };
        groupTable = new JBTable(tableModel);
        groupTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        groupTable.setShowGrid(false);
        groupTable.setRowSelectionAllowed(true);
        groupTable.getTableHeader().setReorderingAllowed(false);
        groupTable.getColumnModel().getColumn(0).setPreferredWidth(55);
        groupTable.getColumnModel().getColumn(0).setMaxWidth(70);
        groupTable.getColumnModel().getColumn(1).setPreferredWidth(145);

        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(groupTable)
                .setAddAction(new AnActionButtonRunnable() {
                    @Override public void run(AnActionButton button) { addGroup(); }
                })
                .setRemoveAction(new AnActionButtonRunnable() {
                    @Override public void run(AnActionButton button) { removeGroup(); }
                })
                .setMoveUpAction(new AnActionButtonRunnable() {
                    @Override public void run(AnActionButton button) { moveGroup(-1); }
                })
                .setMoveDownAction(new AnActionButtonRunnable() {
                    @Override public void run(AnActionButton button) { moveGroup(1); }
                })
                .addExtraAction(new DuplicateGroupAction())
                .addExtraAction(new ExportGroupsAction())
                .addExtraAction(new ImportGroupsAction());

        JPanel leftPanel = decorator.createPanel();
        leftPanel.setBorder(new TitledBorder("Tab Groups"));
        leftPanel.setPreferredSize(new Dimension(220, 0));

        // ===== Right panel: detail editing =====
        JPanel rightPanel = new JPanel(new BorderLayout(5, 10));
        rightPanel.setBorder(new TitledBorder("Group Details"));

        JPanel detailPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        detailPanel.add(new JLabel("Name:"), gbc);

        nameField = new JTextField(20);
        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        detailPanel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        detailPanel.add(new JLabel("Order:"), gbc);

        // min = 0, only non-negative values allowed
        orderSpinner = new JSpinner(new SpinnerNumberModel(0, 0, Integer.MAX_VALUE, 1));
        gbc.gridx = 1; gbc.gridy = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        detailPanel.add(orderSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        detailPanel.add(new JLabel("Regex:"), gbc);

        regexField = new JTextField(30);
        gbc.gridx = 1; gbc.gridy = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        detailPanel.add(regexField, gbc);

        JButton validateBtn = new JButton("Validate Regex");
        gbc.gridx = 1; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0; gbc.anchor = GridBagConstraints.WEST;
        detailPanel.add(validateBtn, gbc);

        rightPanel.add(detailPanel, BorderLayout.NORTH);

        // ===== Assemble =====
        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(rightPanel, BorderLayout.CENTER);

        // ===== Listeners =====
        groupTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || isUpdating) return;
            saveCurrentGroupDetails();
            int selected = groupTable.getSelectedRow();
            loadGroupDetails(selected);
        });

        validateBtn.addActionListener(e -> validateRegex());
        nameField.addActionListener(e -> { if (!isUpdating) saveCurrentGroupDetails(); });
        orderSpinner.addChangeListener(e -> { if (!isUpdating) saveCurrentGroupDetails(); });
        regexField.addActionListener(e -> { if (!isUpdating) saveCurrentGroupDetails(); });

        setDetailsEnabled(false);
    }

    // ===== Add / Remove / Move / Duplicate =====

    private void addGroup() {
        saveCurrentGroupDetails();
        // New group gets order = max existing order + 1 (lands at the bottom)
        int nextOrder = tabGroups.stream().mapToInt(TabGroup::getOrder).max().orElse(-1) + 1;
        TabGroup newGroup = new TabGroup("New Group", nextOrder, "");
        tabGroups.add(newGroup);
        refreshTableAndSelect(tabGroups.size() - 1); // sort will place it last
    }

    private void removeGroup() {
        int idx = groupTable.getSelectedRow();
        if (idx >= 0 && idx < tabGroups.size()) {
            tabGroups.remove(idx);
            currentIndex = -1;
            refreshTableAndSelect(tabGroups.isEmpty() ? -1 : Math.min(idx, tabGroups.size() - 1));
        }
    }

    /**
     * Moves the selected group up ({@code delta=-1}) or down ({@code delta=+1})
     * by swapping the order values of the two adjacent groups, then re-sorting.
     */
    private void moveGroup(int delta) {
        int idx = groupTable.getSelectedRow();
        int target = idx + delta;
        if (idx < 0 || target < 0 || target >= tabGroups.size()) return;

        saveCurrentGroupDetails();

        // Swap list positions
        TabGroup tmp = tabGroups.get(idx);
        tabGroups.set(idx, tabGroups.get(target));
        tabGroups.set(target, tmp);

        // Swap order values so the sort order reflects the new positions
        int orderIdx    = tabGroups.get(idx).getOrder();    // was target's order
        int orderTarget = tabGroups.get(target).getOrder(); // was idx's order
        tabGroups.get(idx).setOrder(orderTarget);
        tabGroups.get(target).setOrder(orderIdx);

        // After sort the moved group lands at `target`
        refreshTableAndSelect(target);
    }

    private void duplicateGroup() {
        int idx = groupTable.getSelectedRow();
        if (idx >= 0 && idx < tabGroups.size()) {
            saveCurrentGroupDetails();
            TabGroup original = tabGroups.get(idx);
            TabGroup duplicate = original.copy();
            duplicate.setName(original.getName() + " (copy)");
            // Give duplicate order = max + 1 so it lands at the end
            int nextOrder = tabGroups.stream().mapToInt(TabGroup::getOrder).max().orElse(-1) + 1;
            duplicate.setOrder(nextOrder);
            tabGroups.add(duplicate);
            refreshTableAndSelect(tabGroups.size() - 1);
        }
    }

    // ===== Public API =====

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

    // ===== Internal helpers =====

    /**
     * Sorts {@code tabGroups} by order, rebuilds the table model, and selects
     * the given row — all under the {@code isUpdating} guard.
     */
    private void refreshTableAndSelect(int selectIndex) {
        // Always keep the list sorted by order
        tabGroups.sort(Comparator.comparingInt(TabGroup::getOrder));

        isUpdating = true;
        try {
            tableModel.setRowCount(0);
            for (TabGroup g : tabGroups) {
                tableModel.addRow(new Object[]{
                    g.getOrder(),
                    g.getName().isEmpty() ? "<unnamed>" : g.getName()
                });
            }
            if (selectIndex >= 0 && selectIndex < tabGroups.size()) {
                groupTable.setRowSelectionInterval(selectIndex, selectIndex);
                // Scroll the selected row into view
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
            TabGroup group = tabGroups.get(currentIndex);
            group.setName(nameField.getText().trim());
            group.setOrder((Integer) orderSpinner.getValue());
            group.setRegex(regexField.getText().trim());
            // Update table row in-place (no resort — user is still editing)
            if (currentIndex < tableModel.getRowCount()) {
                isUpdating = true;
                try {
                    tableModel.setValueAt(group.getOrder(), currentIndex, 0);
                    tableModel.setValueAt(
                        group.getName().isEmpty() ? "<unnamed>" : group.getName(),
                        currentIndex, 1);
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
                TabGroup group = tabGroups.get(index);
                nameField.setText(group.getName());
                orderSpinner.setValue(group.getOrder());
                regexField.setText(group.getRegex());
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

    // ===== Inner actions =====

    private class DuplicateGroupAction extends AnAction {
        DuplicateGroupAction() {
            super("Duplicate", "Duplicate the selected tab group", AllIcons.Actions.Copy);
        }
        @Override public void actionPerformed(@NotNull AnActionEvent e) { duplicateGroup(); }
        @Override public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(groupTable.getSelectedRow() >= 0);
        }
    }

    private class ExportGroupsAction extends AnAction {
        ExportGroupsAction() {
            super("Export", "Export all tab groups to a JSON file", AllIcons.Actions.Upload);
        }
        @Override public void actionPerformed(@NotNull AnActionEvent e) {
            saveCurrentGroupDetails();
            TabGroupsPorter.export(new ArrayList<>(tabGroups), mainPanel);
        }
        @Override public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(!tabGroups.isEmpty());
        }
    }

    private class ImportGroupsAction extends AnAction {
        ImportGroupsAction() {
            super("Import", "Import tab groups from a JSON file (replaces current configuration)", AllIcons.Actions.Download);
        }
        @Override public void actionPerformed(@NotNull AnActionEvent e) {
            List<TabGroup> imported = TabGroupsPorter.importGroups(mainPanel);
            if (imported != null) setTabGroups(imported);
        }
        @Override public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(true);
        }
    }
}
