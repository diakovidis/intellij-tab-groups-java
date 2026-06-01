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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared logic for reordering tabs. Primary strategy: {@code JBTabsImpl.sortTabs()}
 * — in-place, zero flicker, same mechanism as drag-and-drop.
 * <p>
 * Falls back to close/reopen if the primary strategy is unavailable.
 * A reentrance guard prevents the auto-sort listener from triggering recursively
 * during fallback close/reopen operations.
 */
public final class TabReorderExecutor {

    private static final Logger LOG = Logger.getInstance(TabReorderExecutor.class);

    /**
     * Set to {@code true} while a close/reopen reorder is in progress so the
     * {@code TabAutoSorterListener} does not trigger a second reorder.
     */
    private static final AtomicBoolean IS_REORDERING = new AtomicBoolean(false);

    private TabReorderExecutor() {
    }

    /** Returns {@code true} while a reorder operation is in progress. */
    public static boolean isReordering() {
        return IS_REORDERING.get();
    }

    /**
     * Reorders tabs using persisted {@link TabGroupsSettings}.
     *
     * @return the number of editor windows that were sorted (always 0 if
     *         the work is deferred to the EDT via {@code invokeLater}).
     */
    public static int reorder(@NotNull Project project) {
        return reorder(project, TabGroupsSettings.getInstance(project).getTabGroups());
    }

    /**
     * Reorders all open (unpinned) editor tabs using the supplied tab-group rules.
     */
    public static int reorder(@NotNull Project project, @NotNull List<TabGroup> tabGroups) {
        if (project.isDisposed()) return 0;

        // Pre-sort groups once so matching priority is stable
        List<TabGroup> sortedGroups = new ArrayList<>(tabGroups);
        sortedGroups.sort(Comparator.comparingInt(TabGroup::getOrder));

        int defaultOrder = sortedGroups.isEmpty()
                ? Integer.MAX_VALUE
                : sortedGroups.stream().mapToInt(TabGroup::getOrder).max().orElse(0) + 1;

        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            try {
                FileEditorManagerEx managerEx = FileEditorManagerEx.getInstanceEx(project);
                EditorWindow[] windows = managerEx.getWindows();

                int sortedWindowCount = 0;
                for (EditorWindow window : windows) {
                    if (sortWindowInPlace(window, sortedGroups, defaultOrder)) {
                        sortedWindowCount++;
                    }
                }

                // Fallback: if in-place sort didn't fire for any window, use close/reopen
                if (sortedWindowCount == 0 && windows.length > 0) {
                    LOG.warn("TabReorderExecutor: in-place sort unavailable — falling back to close/reopen.");
                    reorderViaCloseReopen(project, sortedGroups, defaultOrder);
                } else {
                    LOG.info("TabReorderExecutor: sorted " + sortedWindowCount + " window(s) in-place.");
                }
            } catch (Exception ex) {
                LOG.warn("TabReorderExecutor: reorder failed", ex);
            }
        });

        return 0; // actual work is async
    }

    // ── Primary strategy: JBTabsImpl.sortTabs() ──────────────────────────────

    /**
     * Sorts a single editor window in-place using {@code JBTabsImpl.sortTabs()}.
     *
     * <p>The key fix: instead of relying on {@code tabInfo.getObject()} (whose
     * concrete type changes across IDE versions), we build a position-based
     * {@code TabInfo → VirtualFile} map using {@code window.getFileList()}.
     * Both lists are in the same visual order, so index {@code i} in one
     * corresponds to index {@code i} in the other.
     *
     * @return {@code true} if the sort was applied.
     */
    @SuppressWarnings("UnstableApiUsage")
    private static boolean sortWindowInPlace(@NotNull EditorWindow window,
                                             @NotNull List<TabGroup> sortedGroups,
                                             int defaultOrder) {
        // ── 1. Access JBTabsImpl ─────────────────────────────────────────────
        JBTabsImpl jbTabs;
        try {
            var tabbedPane = window.getTabbedPane();
            if (tabbedPane == null) {
                LOG.info("TabReorderExecutor: getTabbedPane() returned null.");
                return false;
            }
            var tabs = tabbedPane.getTabs();
            if (!(tabs instanceof JBTabsImpl impl)) {
                LOG.info("TabReorderExecutor: tabs is not JBTabsImpl but " +
                         (tabs == null ? "null" : tabs.getClass().getName()));
                return false;
            }
            jbTabs = impl;
        } catch (Exception e) {
            LOG.info("TabReorderExecutor: could not access JBTabsImpl — " + e.getMessage());
            return false;
        }

        List<TabInfo> tabInfoList = new ArrayList<>(jbTabs.getTabs());
        if (tabInfoList.isEmpty()) return false;

        // ── 2. Position-based TabInfo → VirtualFile mapping ─────────────────
        //    window.getFileList() returns files in the same visual order as jbTabs.getTabs().
        List<VirtualFile> fileList = window.getFileList();
        Map<TabInfo, VirtualFile> tabToFile = new IdentityHashMap<>();
        for (int i = 0; i < Math.min(tabInfoList.size(), fileList.size()); i++) {
            tabToFile.put(tabInfoList.get(i), fileList.get(i));
        }
        LOG.info("TabReorderExecutor: window has " + tabInfoList.size() +
                 " tab(s), " + fileList.size() + " file(s) mapped.");

        // ── 3. Snapshot original indices (pinned tabs keep their relative order) ──
        Map<TabInfo, Integer> originalIndex = new IdentityHashMap<>();
        for (int i = 0; i < tabInfoList.size(); i++) {
            originalIndex.put(tabInfoList.get(i), i);
        }

        long unpinnedCount = tabInfoList.stream().filter(t -> !t.isPinned()).count();
        if (unpinnedCount == 0) {
            LOG.info("TabReorderExecutor: all tabs are pinned — skipping.");
            return false;
        }

        // ── 4. Sort via JBTabsImpl (same as drag-and-drop internally) ────────
        jbTabs.sortTabs((a, b) -> {
            boolean aPinned = a.isPinned();
            boolean bPinned = b.isPinned();

            // Pinned tabs stay at the front in their original relative order
            if (aPinned && bPinned)
                return Integer.compare(
                        originalIndex.getOrDefault(a, 0),
                        originalIndex.getOrDefault(b, 0));
            if (aPinned) return -1;
            if (bPinned) return 1;

            VirtualFile fileA = tabToFile.get(a);
            VirtualFile fileB = tabToFile.get(b);

            int keyA = sortKey(fileA, sortedGroups, defaultOrder);
            int keyB = sortKey(fileB, sortedGroups, defaultOrder);
            if (keyA != keyB) return Integer.compare(keyA, keyB);

            // Same group → stable alphabetical by filename
            String nameA = fileA != null ? fileA.getName() : "";
            String nameB = fileB != null ? fileB.getName() : "";
            return String.CASE_INSENSITIVE_ORDER.compare(nameA, nameB);
        });

        LOG.info("TabReorderExecutor: in-place sort applied to " + unpinnedCount + " unpinned tab(s).");
        return true;
    }

    // ── Fallback strategy: close / reopen ────────────────────────────────────

    /**
     * Fallback reorder using close/reopen when {@code JBTabsImpl} is unavailable.
     * Guards against recursive triggering by the auto-sort listener.
     */
    @SuppressWarnings("UnstableApiUsage")
    private static void reorderViaCloseReopen(@NotNull Project project,
                                               @NotNull List<TabGroup> sortedGroups,
                                               int defaultOrder) {
        if (!IS_REORDERING.compareAndSet(false, true)) return; // already running
        try {
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

            // Sort using TabSorter
            List<VirtualFile> sorted = TabSorter.sort(unpinned, sortedGroups);

            // Remember focused file
            FileEditor sel = editorManager.getSelectedEditor();
            VirtualFile selectedFile = sel != null ? sel.getFile() : null;

            // Close unpinned, reopen sorted
            for (VirtualFile f : unpinned) editorManager.closeFile(f);
            for (VirtualFile f : sorted)   editorManager.openFile(f, false, false);
            if (selectedFile != null && selectedFile.isValid()) {
                editorManager.openFile(selectedFile, true, true);
            }
            LOG.info("TabReorderExecutor: fallback close/reopen sorted " + sorted.size() + " tab(s).");
        } finally {
            IS_REORDERING.set(false);
        }
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
}
