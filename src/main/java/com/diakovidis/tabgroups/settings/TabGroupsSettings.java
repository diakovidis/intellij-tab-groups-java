package com.diakovidis.tabgroups.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.diakovidis.tabgroups.model.TabGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Persists Tab Groups settings per-project.
 */
@Service(Service.Level.PROJECT)
@State(
        name = "TabGroupsSettings",
        storages = @Storage("tabGroups.xml")
)
public final class TabGroupsSettings implements PersistentStateComponent<TabGroupsSettings.State> {

    private State myState = new State();

    public static TabGroupsSettings getInstance(@NotNull Project project) {
        return project.getService(TabGroupsSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }

    /**
     * Returns a deep copy of the current tab groups.
     */
    public List<TabGroup> getTabGroups() {
        List<TabGroup> copy = new ArrayList<>();
        for (TabGroupState gs : myState.tabGroups) {
            copy.add(new TabGroup(gs.name, gs.order, gs.regex));
        }
        return copy;
    }

    /**
     * Sets the tab groups from the given list (deep copies into state).
     */
    public void setTabGroups(List<TabGroup> tabGroups) {
        List<TabGroupState> stateList = new ArrayList<>();
        for (TabGroup group : tabGroups) {
            TabGroupState gs = new TabGroupState();
            gs.name = group.getName();
            gs.order = group.getOrder();
            gs.regex = group.getRegex();
            stateList.add(gs);
        }
        myState.tabGroups = stateList;
    }

    /**
     * Serializable state holder. Public fields are required for XML serialization.
     */
    public static class State {
        public List<TabGroupState> tabGroups = new ArrayList<>();
    }

    /**
     * Serializable representation of a single TabGroup.
     */
    public static class TabGroupState {
        public String name = "";
        public int order = 0;
        public String regex = "";
    }
}
