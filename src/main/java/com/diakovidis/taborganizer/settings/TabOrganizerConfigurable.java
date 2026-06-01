package com.diakovidis.taborganizer.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.diakovidis.taborganizer.model.TabGroup;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Settings configurable for Tab Organizer.
 * Appears under Settings > Tools > Tab Organizer.
 */
public class TabOrganizerConfigurable implements Configurable {

    private final Project project;
    private TabOrganizerSettingsPanel settingsPanel;

    public TabOrganizerConfigurable(Project project) {
        this.project = project;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Tab Organizer";
    }

    @Override
    public @Nullable JComponent createComponent() {
        settingsPanel = new TabOrganizerSettingsPanel();
        return settingsPanel.getPanel();
    }

    @Override
    public boolean isModified() {
        if (settingsPanel == null) {
            return false;
        }
        TabOrganizerSettings settings = TabOrganizerSettings.getInstance(project);
        List<TabGroup> saved = settings.getTabGroups();
        List<TabGroup> current = settingsPanel.getTabGroups();
        return !tabGroupsEqual(saved, current);
    }

    @Override
    public void apply() {
        if (settingsPanel == null) {
            return;
        }
        TabOrganizerSettings settings = TabOrganizerSettings.getInstance(project);
        settings.setTabGroups(settingsPanel.getTabGroups());
    }

    @Override
    public void reset() {
        if (settingsPanel == null) {
            return;
        }
        TabOrganizerSettings settings = TabOrganizerSettings.getInstance(project);
        settingsPanel.setTabGroups(settings.getTabGroups());
    }

    @Override
    public void disposeUIResources() {
        settingsPanel = null;
    }

    private boolean tabGroupsEqual(List<TabGroup> a, List<TabGroup> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            TabGroup ga = a.get(i);
            TabGroup gb = b.get(i);
            if (!ga.getName().equals(gb.getName())) return false;
            if (ga.getOrder() != gb.getOrder()) return false;
            if (!ga.getRegex().equals(gb.getRegex())) return false;
        }
        return true;
    }
}
