package com.diakovidis.tabgroups.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.diakovidis.tabgroups.model.TabGroup;
import com.diakovidis.tabgroups.settings.TabGroupsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared logic for reordering tabs. Primary strategy: {@code JBTabsImpl.sortTabs()}
 * — in-place, zero flicker, same mechanism as drag-and-drop.
 * <p>
 * Falls back to close/reopen if the primary strategy is unavailable.
 * Triggered only by the user via the "Group and Sort Tabs" context action,
 * or by the "Test" button in Settings.
 */
public final class TabReorderExecutor {

    private static final Logger LOG = Logger.getInstance(TabReorderExecutor.class);

    private TabReorderExecutor() {
    }

    /**
     * Reorders tabs in the given project using the persisted {@link TabGroupsSettings}.
     *
     * @return the number of editor windows that were sorted.
     */
    public static int reorder(@NotNull Project project) {
        return reorder(project, TabGroupsSettings.getInstance(project).getTabGroups());
    }

    /**
     * Reorders all open (unpinned) editor tabs using the supplied tab-group rules.
     * Each editor window is sorted independently.
     * <p>
     * Uses {@code JBTabsImpl.sortTabs(Comparator)} — the same call the platform
     * makes when the user finishes a drag-and-drop tab move.
     *
     * @return the number of editor windows that were sorted, or 0 if nothing happened.
     */
    public static int reorder(@NotNull Project project, @NotNull List<TabGroup> tabGroups) {
        if (project.isDisposed()) return 0;

        // Pre-sort groups once (ascending by order) so matching priority is stable
        List<TabGroup> sortedGroups = new ArrayList<>(tabGroups);
        sortedGroups.sort(Comparator.comparingInt(TabGroup::getOrder));

        int defaultOrder = sortedGroups.isEmpty() ? Integer.MAX_VALUE
                : sortedGroups.stream().mapToInt(TabGroup::getOrder).max().orElse(0) + 1;

        int[] sortedWindows = {0};

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                FileEditorManagerEx managerEx = FileEditorManagerEx.getInstanceEx(project);
                for (EditorWindow window : managerEx.getWindows()) {
                    if (sortWindow(window, sortedGroups, defaultOrder)) {
                        sortedWindows[0]++;
                    }
                }
            } catch (Exception ex) {
                LOG.warn("TabReorderExecutor: reorder failed", ex);
            }
            LOG.info("TabReorderExecutor: sorted " + sortedWindows[0] + " window(s).");
        });

        return sortedWindows[0];
    }

    /**
     * Sorts a single editor window's tabs using {@code JBTabsImpl.sortTabs()}.
     *
     * @return {@code true} if sorting was applied, {@code false} if skipped.
     */
    @SuppressWarnings("UnstableApiUsage")
    private static boolean sortWindow(@NotNull EditorWindow window,
                                      @NotNull List<TabGroup> sortedGroups,
                                      int defaultOrder) {
        JBTabs tabs = window.getTabbedPane().getTabs();
        if (!(tabs instanceof JBTabsImpl jbTabs)) return false;

        List<TabInfo> current = jbTabs.getTabs();
        if (current.isEmpty()) return false;

        // Snapshot original indices so pinned tabs stay in their relative order
        Map<TabInfo, Integer> originalIndex = new HashMap<>();
        for (int i = 0; i < current.size(); i++) {
            originalIndex.put(current.get(i), i);
        }

        long unpinnedCount = current.stream().filter(t -> !t.isPinned()).count();
        if (unpinnedCount == 0) {
            LOG.info("TabReorderExecutor: all tabs pinned in window — skipping.");
            return false;
        }

        // sortTabs() calls reorderTab(TabInfo, int) internally for each
        // out-of-place tab — identical to what happens after a drag-and-drop.
        jbTabs.sortTabs((a, b) -> {
            boolean aPinned = a.isPinned();
            boolean bPinned = b.isPinned();

            // Pinned tabs stay at the front in their original relative order
            if (aPinned && bPinned)
                return Integer.compare(originalIndex.getOrDefault(a, 0),
                                       originalIndex.getOrDefault(b, 0));
            if (aPinned) return -1;
            if (bPinned) return 1;

            // Both unpinned → apply group sort key
            int keyA = sortKey(getFile(a), sortedGroups, defaultOrder);
            int keyB = sortKey(getFile(b), sortedGroups, defaultOrder);
            if (keyA != keyB) return Integer.compare(keyA, keyB);

            // Same group → alphabetical by filename
            return String.CASE_INSENSITIVE_ORDER.compare(getFileName(a), getFileName(b));
        });

        LOG.info("TabReorderExecutor: sorted " + unpinnedCount + " unpinned tab(s) in window.");
        return true;
    }

    /**
     * Fallback reorder using close/reopen when {@code JBTabsImpl} is unavailable.
     */
    @SuppressWarnings("UnstableApiUsage")
    private static void reorderViaCloseReopen(@NotNull Project project,
                                               @NotNull List<TabGroup> sortedGroups,
                                               int defaultOrder) {
        FileEditorManager editorManager = FileEditorManager.getInstance(project);
        List<VirtualFile> allOpen = List.of(editorManager.getOpenFiles());
        if (allOpen.isEmpty()) return;

        // Collect pinned files to leave them untouched
        FileEditorManagerEx managerEx = FileEditorManagerEx.getInstanceEx(project);
        java.util.Set<VirtualFile> pinned = new java.util.HashSet<>();
        try {
            for (EditorWindow w : managerEx.getWindows()) {
                for (VirtualFile f : w.getFileList()) {
                    if (w.isFilePinned(f)) pinned.add(f);
                }
            }
        } catch (Exception e) {
            LOG.warn("TabReorderExecutor: could not detect pinned tabs", e);
        }

        List<VirtualFile> unpinned = new ArrayList<>();
        for (VirtualFile f : allOpen) {
            if (!pinned.contains(f)) unpinned.add(f);
        }
        if (unpinned.isEmpty()) return;

        List<VirtualFile> sorted = TabSorter.sort(unpinned, sortedGroups);

        FileEditor sel = editorManager.getSelectedEditor();
        VirtualFile selectedFile = sel != null ? sel.getFile() : null;

        for (VirtualFile f : unpinned) editorManager.closeFile(f);
        for (VirtualFile f : sorted)   editorManager.openFile(f, false, false);
        if (selectedFile != null && selectedFile.isValid()) {
            editorManager.openFile(selectedFile, true, true);
        }
        LOG.info("TabReorderExecutor: fallback close/reopen sorted " + sorted.size() + " tab(s).");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static int sortKey(@Nullable VirtualFile file,
                                @NotNull List<TabGroup> sortedGroups,
                                int defaultOrder) {
        if (file == null) return defaultOrder;
        for (TabGroup group : sortedGroups) {
            if (TabGroupMatcher.matches(file, group)) return group.getOrder();
        }
        return defaultOrder;
    }

    private static @Nullable VirtualFile getFile(@NotNull TabInfo tabInfo) {
        Object obj = tabInfo.getObject();
        if (obj instanceof EditorComposite composite) return composite.getFile();
        return null;
    }

    private static @NotNull String getFileName(@NotNull TabInfo tabInfo) {
        VirtualFile file = getFile(tabInfo);
        return file != null ? file.getName() : "";
    }
}
