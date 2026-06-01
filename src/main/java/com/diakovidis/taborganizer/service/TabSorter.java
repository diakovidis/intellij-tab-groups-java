package com.diakovidis.taborganizer.service;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.diakovidis.taborganizer.model.TabGroup;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service responsible for sorting open files according to tab group rules.
 */
public final class TabSorter {

    private static final Logger LOG = Logger.getInstance(TabSorter.class);

    private TabSorter() {
    }

    /**
     * Sorts the given files based on tab group rules.
     * <p>
     * Each file is matched against the tab groups (in order priority).
     * The first matching group wins. Unmatched files go to the end.
     * Within each group, files are sorted alphabetically by name.
     *
     * @param files     the open files to sort
     * @param tabGroups the user-defined tab groups (need not be pre-sorted)
     * @return a new list of files in the desired tab order
     */
    public static List<VirtualFile> sort(List<VirtualFile> files, List<TabGroup> tabGroups) {
        // Sort groups by order ascending for matching priority
        List<TabGroup> sortedGroups = new ArrayList<>(tabGroups);
        sortedGroups.sort(Comparator.comparingInt(TabGroup::getOrder));

        // Default order for unmatched files: one higher than the max group order
        int defaultOrder = sortedGroups.isEmpty()
                ? Integer.MAX_VALUE
                : sortedGroups.stream().mapToInt(TabGroup::getOrder).max().orElse(0) + 1;

        // Build entries with resolved group order
        List<SortEntry> entries = new ArrayList<>();
        for (VirtualFile file : files) {
            int groupOrder = defaultOrder;
            String groupName = "<default>";

            for (TabGroup group : sortedGroups) {
                if (TabGroupMatcher.matches(file, group)) {
                    groupOrder = group.getOrder();
                    groupName = group.getName();
                    break; // First match wins
                }
            }

            LOG.debug("File: " + file.getName() + " -> group: " + groupName + " (order=" + groupOrder + ")");
            entries.add(new SortEntry(file, groupOrder));
        }

        // Sort: primary by group order ASC, secondary by filename alphabetical
        entries.sort(Comparator
                .comparingInt(SortEntry::groupOrder)
                .thenComparing(e -> e.file().getName(), String.CASE_INSENSITIVE_ORDER));

        List<VirtualFile> sorted = new ArrayList<>();
        for (SortEntry entry : entries) {
            sorted.add(entry.file());
        }
        return sorted;
    }

    private record SortEntry(VirtualFile file, int groupOrder) {
    }
}

