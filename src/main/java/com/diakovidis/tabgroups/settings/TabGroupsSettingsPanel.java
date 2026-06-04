package com.diakovidis.tabgroups.settings;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.diakovidis.tabgroups.model.TabGroup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Swing panel for editing Tab Groups settings.
 * Provides a list of Tab Groups on the left, and group details (name, order, regex) on the right.
 */
public class TabGroupsSettingsPanel {

    private final JPanel mainPanel;

    // Left side: group list
    private final DefaultListModel<TabGroup> groupListModel;
    private final JBList<TabGroup> groupList;

    // Right side: detail editing
    private final JTextField nameField;
    private final JSpinner orderSpinner;
    private final JTextField regexField;

    // Data
    private final List<TabGroup> tabGroups = new ArrayList<>();
    private int currentIndex = -1;

    /** Guard flag to suppress selection listener during programmatic list updates. */
    private boolean isUpdating = false;

    public TabGroupsSettingsPanel() {
        mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ===== Left panel: group list with IntelliJ ToolbarDecorator (+, -, copy) =====
        groupListModel = new DefaultListModel<>();
        groupList = new JBList<>(groupListModel);
        groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        groupList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof TabGroup group) {
                    setText(group.getName().isEmpty() ? "<unnamed>" : group.getName());
                }
                return this;
            }
        });

        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(groupList)
                .setAddAction(new AnActionButtonRunnable() {
                    @Override
                    public void run(AnActionButton button) {
                        addGroup();
                    }
                })
                .setRemoveAction(new AnActionButtonRunnable() {
                    @Override
                    public void run(AnActionButton button) {
                        removeGroup();
                    }
                })
                .setMoveUpAction(new AnActionButtonRunnable() {
                    @Override
                    public void run(AnActionButton button) {
                        moveGroup(-1);
                    }
                })
                .setMoveDownAction(new AnActionButtonRunnable() {
                    @Override
                    public void run(AnActionButton button) {
                        moveGroup(1);
                    }
                })
                .addExtraAction(new DuplicateGroupAction())
                .addExtraAction(new ExportGroupsAction())
                .addExtraAction(new ImportGroupsAction());

        JPanel leftPanel = decorator.createPanel();
        leftPanel.setBorder(new TitledBorder("Tab Groups"));
        leftPanel.setPreferredSize(new Dimension(200, 0));

        // ===== Right panel: detail editing =====
        JPanel rightPanel = new JPanel(new BorderLayout(5, 10));
        rightPanel.setBorder(new TitledBorder("Group Details"));

        // Detail fields: name, order, regex
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

        orderSpinner = new JSpinner(new SpinnerNumberModel(0, Integer.MIN_VALUE, Integer.MAX_VALUE, 1));
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

        // ===== Assemble main panel =====
        mainPanel.add(leftPanel, BorderLayout.WEST);
        mainPanel.add(rightPanel, BorderLayout.CENTER);

        // ===== Wire up listeners =====

        // When selecting a group in the list, save current and load selected
        groupList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || isUpdating) return;
            saveCurrentGroupDetails();
            int selected = groupList.getSelectedIndex();
            loadGroupDetails(selected);
        });

        // Validate regex
        validateBtn.addActionListener(e -> validateRegex());

        // Apply name/order/regex changes on Enter / spinner change
        // Guard with isUpdating so that programmatic changes in loadGroupDetails() don't trigger saves.
        nameField.addActionListener(e -> { if (!isUpdating) saveCurrentGroupDetails(); });
        orderSpinner.addChangeListener(e -> { if (!isUpdating) saveCurrentGroupDetails(); });
        regexField.addActionListener(e -> { if (!isUpdating) saveCurrentGroupDetails(); });

        // Initially disable details
        setDetailsEnabled(false);
    }

    // ===== Add / Remove / Duplicate actions =====

    private void addGroup() {
        saveCurrentGroupDetails();
        TabGroup newGroup = new TabGroup("New Group", tabGroups.size(), "");
        tabGroups.add(newGroup);
        refreshGroupListAndSelect(tabGroups.size() - 1);
    }

    private void removeGroup() {
        int idx = groupList.getSelectedIndex();
        if (idx >= 0 && idx < tabGroups.size()) {
            tabGroups.remove(idx);
            currentIndex = -1;
            if (!tabGroups.isEmpty()) {
                refreshGroupListAndSelect(Math.min(idx, tabGroups.size() - 1));
            } else {
                refreshGroupListAndSelect(-1);
            }
        }
    }

    /**
     * Moves the currently selected group up ({@code delta=-1}) or down ({@code delta=+1}).
     * Swaps both the backing {@code tabGroups} list AND the {@code groupListModel} atomically
     * so they never diverge.
     */
    private void moveGroup(int delta) {
        int idx = groupList.getSelectedIndex();
        int target = idx + delta;
        if (idx < 0 || target < 0 || target >= tabGroups.size()) return;

        saveCurrentGroupDetails();

        // Swap in the backing list
        TabGroup tmp = tabGroups.get(idx);
        tabGroups.set(idx, tabGroups.get(target));
        tabGroups.set(target, tmp);

        // Swap the order values so the sort key follows the new position
        int orderA = tabGroups.get(idx).getOrder();   // was target's order
        int orderB = tabGroups.get(target).getOrder(); // was idx's order
        tabGroups.get(idx).setOrder(orderB);
        tabGroups.get(target).setOrder(orderA);

        // Rebuild the list model and move selection — all under isUpdating guard
        refreshGroupListAndSelect(target);
    }

    private void duplicateGroup() {
        int idx = groupList.getSelectedIndex();
        if (idx >= 0 && idx < tabGroups.size()) {
            saveCurrentGroupDetails();
            TabGroup original = tabGroups.get(idx);
            TabGroup duplicate = original.copy();
            duplicate.setName(original.getName() + " (copy)");
            tabGroups.add(idx + 1, duplicate);
            refreshGroupListAndSelect(idx + 1);
        }
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    /**
     * Returns the tab groups as currently configured in the UI.
     */
    public List<TabGroup> getTabGroups() {
        saveCurrentGroupDetails();
        List<TabGroup> result = new ArrayList<>();
        for (TabGroup g : tabGroups) {
            result.add(g.copy());
        }
        return result;
    }

    /**
     * Sets the tab groups to display in the UI.
     */
    public void setTabGroups(List<TabGroup> groups) {
        currentIndex = -1;
        tabGroups.clear();
        for (TabGroup g : groups) {
            tabGroups.add(g.copy());
        }
        if (!tabGroups.isEmpty()) {
            refreshGroupListAndSelect(0);
        } else {
            refreshGroupListAndSelect(-1);
        }
    }

    /**
     * Refreshes the list model and selects the given index, suppressing
     * intermediate selection events to avoid the overwrite bug.
     */
    private void refreshGroupListAndSelect(int selectIndex) {
        isUpdating = true;
        try {
            groupListModel.clear();
            for (TabGroup g : tabGroups) {
                groupListModel.addElement(g);
            }
            if (selectIndex >= 0 && selectIndex < tabGroups.size()) {
                groupList.setSelectedIndex(selectIndex);
            } else {
                groupList.clearSelection();
            }
        } finally {
            isUpdating = false;
        }
        // Now manually load the selected group details
        loadGroupDetails(selectIndex);
    }

    private void saveCurrentGroupDetails() {
        if (currentIndex >= 0 && currentIndex < tabGroups.size()) {
            TabGroup group = tabGroups.get(currentIndex);
            group.setName(nameField.getText().trim());
            group.setOrder((Integer) orderSpinner.getValue());
            group.setRegex(regexField.getText().trim());
            // Update list display — guard with isUpdating to prevent the set()
            // from firing a selection event that re-enters save/load and overwrites other groups.
            if (currentIndex < groupListModel.size()) {
                isUpdating = true;
                try {
                    groupListModel.set(currentIndex, group);
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

    // ===== Inner action for the duplicate / copy button =====

    private class DuplicateGroupAction extends AnAction {

        DuplicateGroupAction() {
            super("Duplicate", "Duplicate the selected tab group", AllIcons.Actions.Copy);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            duplicateGroup();
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(groupList.getSelectedIndex() >= 0);
        }
    }

    // ===== Inner action for Export =====

    private class ExportGroupsAction extends AnAction {

        ExportGroupsAction() {
            super("Export", "Export all tab groups to a JSON file", AllIcons.Actions.Upload);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            saveCurrentGroupDetails();
            TabGroupsPorter.export(new java.util.ArrayList<>(tabGroups), mainPanel);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(!tabGroups.isEmpty());
        }
    }

    // ===== Inner action for Import =====

    private class ImportGroupsAction extends AnAction {

        ImportGroupsAction() {
            super("Import", "Import tab groups from a JSON file (replaces current configuration)", AllIcons.Actions.Download);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
            java.util.List<TabGroup> imported = TabGroupsPorter.importGroups(mainPanel);
            if (imported != null) {
                setTabGroups(imported);
            }
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
            e.getPresentation().setEnabled(true); // always available
        }
    }
}
