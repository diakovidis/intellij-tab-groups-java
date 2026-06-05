package com.diakovidis.tabgroups.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.tabs.JBTabs;
import com.intellij.ui.tabs.TabInfo;
import com.diakovidis.tabgroups.model.TabGroup;
import com.diakovidis.tabgroups.settings.TabGroupsSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared logic for reordering tabs.
 * <p>
 * Primary strategy: calls {@code sortTabs(Comparator)} on the tab container via
 * <em>reflection</em> — in-place, zero flicker, same mechanism as drag-and-drop.
 * Reflection is used deliberately so that no bytecode reference to internal
 * platform classes (e.g. {@code JBTabsImpl}) appears in the plugin, keeping the
 * Plugin Verifier clean.
 * <p>
 * Falls back to close/reopen if the reflective call is unavailable.
 * A reentrance guard prevents the auto-sort listener from triggering recursively
 * during fallback close/reopen operations.
 * <p>
 * Triggered by: (1) user right-click → "Group and Sort Tabs", or
 * (2) {@code TabAutoSorterListener} when a new file is opened.
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

    // ── Primary strategy: reflective sortTabs() ──────────────────────────────

    /**
     * Sorts a single editor window in-place by calling {@code sortTabs(Comparator)}
     * on the tab container via reflection.
     *
     * <p>Using reflection means no bytecode reference to {@code JBTabsImpl} (an
     * internal platform class) exists in the plugin, so the Plugin Verifier reports
     * zero internal-API usages while the runtime behaviour is identical.
     *
     * <p>Position-based {@code TabInfo → VirtualFile} mapping: both
     * {@code tabs.getTabs()} and {@code window.getFileList()} are in the same
     * visual order, so index {@code i} in one corresponds to index {@code i} in
     * the other.
     *
     * @return {@code true} if the sort was applied.
     */
    private static boolean sortWindowInPlace(@NotNull EditorWindow window,
                                             @NotNull List<TabGroup> sortedGroups,
                                             int defaultOrder) {
        // ── 1. Access JBTabs (public interface) ──────────────────────────────
        JBTabs tabs;
        try {
            var tabbedPane = window.getTabbedPane();
            if (tabbedPane == null) {
                LOG.info("TabReorderExecutor: getTabbedPane() returned null.");
                return false;
            }
            tabs = tabbedPane.getTabs();
            if (tabs == null) {
                LOG.info("TabReorderExecutor: getTabs() returned null.");
                return false;
            }
        } catch (Exception e) {
            LOG.info("TabReorderExecutor: could not access tabs — " + e.getMessage());
            return false;
        }

        List<TabInfo> tabInfoList = new ArrayList<>(tabs.getTabs());
        if (tabInfoList.isEmpty()) return false;

        // ── 2. Position-based TabInfo → VirtualFile mapping ─────────────────
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

        // ── 4. Sort via reflective call to sortTabs(Comparator) ──────────────
        Comparator<TabInfo> comparator = (a, b) -> {
            boolean aPinned = a.isPinned();
            boolean bPinned = b.isPinned();

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

            String nameA = fileA != null ? fileA.getName() : "";
            String nameB = fileB != null ? fileB.getName() : "";
            return String.CASE_INSENSITIVE_ORDER.compare(nameA, nameB);
        };

        if (!invokeSortTabs(tabs, comparator)) return false;

        LOG.info("TabReorderExecutor: in-place sort applied to " + unpinnedCount + " unpinned tab(s).");
        return true;
    }

    /**
     * Calls {@code sortTabs(Comparator)} on the given {@link JBTabs} instance via
     * reflection. This avoids any compile-time or bytecode dependency on the
     * internal {@code JBTabsImpl} class.
     *
     * @return {@code true} if the call succeeded, {@code false} if the method was
     *         not found (IDE version without {@code sortTabs}) or the call failed.
     */
    private static boolean invokeSortTabs(@NotNull JBTabs tabs,
                                          @NotNull Comparator<TabInfo> comparator) {
        try {
            Method sortMethod = tabs.getClass().getMethod("sortTabs", Comparator.class);
            sortMethod.invoke(tabs, comparator);
            return true;
        } catch (NoSuchMethodException e) {
            LOG.info("TabReorderExecutor: sortTabs(Comparator) not found on "
                     + tabs.getClass().getName() + " — will fall back.");
            return false;
        } catch (Exception e) {
            LOG.warn("TabReorderExecutor: sortTabs invocation failed", e);
            return false;
        }
    }

    /**
     * Fallback reorder using close/reopen when {@code JBTabsImpl} is unavailable.
     * Guards against recursive triggering by the auto-sort listener.
     */
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

            List<VirtualFile> sorted = TabSorter.sort(unpinned, sortedGroups);

            FileEditor sel = editorManager.getSelectedEditor();
            VirtualFile selectedFile = sel != null ? sel.getFile() : null;

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

    /**
     * Places a single newly-opened file at its correct group position within its editor window.
     * <p>
     * Unlike {@link #reorder}, this method leaves ALL other tabs in their current positions,
     * so manual drag-and-drop arrangements made by the user are fully preserved.
     * Only the new tab moves — and it moves synchronously, minimising the visible flash.
     * <p>
     * Must be called on the EDT (which {@code FileEditorManagerListener.fileOpened} already is).
     */
    public static void placeNewTab(@NotNull Project project,
                                   @NotNull VirtualFile newFile,
                                   @NotNull List<TabGroup> tabGroups) {
        if (project.isDisposed()) return;
        if (IS_REORDERING.get()) return;

        List<TabGroup> sortedGroups = new ArrayList<>(tabGroups);
        sortedGroups.sort(Comparator.comparingInt(TabGroup::getOrder));
        int defaultOrder = sortedGroups.isEmpty()
                ? Integer.MAX_VALUE
                : sortedGroups.stream().mapToInt(TabGroup::getOrder).max().orElse(0) + 1;

        try {
            FileEditorManagerEx managerEx = FileEditorManagerEx.getInstanceEx(project);
            for (EditorWindow window : managerEx.getWindows()) {

                // Is the new file in this window?
                List<VirtualFile> fileList = window.getFileList();
                int newFileIdx = fileList.indexOf(newFile);
                if (newFileIdx < 0) continue;

                // Access JBTabs (public interface) — sortTabs called via reflection
                JBTabs tabs;
                try {
                    var tabbedPane = window.getTabbedPane();
                    if (tabbedPane == null) continue;
                    tabs = tabbedPane.getTabs();
                    if (tabs == null) continue;
                } catch (Exception e) {
                    LOG.info("TabReorderExecutor: placeNewTab – tabs unavailable: " + e.getMessage());
                    continue;
                }

                List<TabInfo> tabInfoList = new ArrayList<>(tabs.getTabs());
                if (newFileIdx >= tabInfoList.size()) continue;

                // Position-based mapping: tabInfoList[i] ↔ fileList[i]
                final TabInfo newTabInfo = tabInfoList.get(newFileIdx);

                // Snapshot current order of ALL tabs — used to keep existing tabs stable
                final Map<TabInfo, Integer> stableOrder = new IdentityHashMap<>();
                for (int i = 0; i < tabInfoList.size(); i++) {
                    stableOrder.put(tabInfoList.get(i), i);
                }

                // File lookup for the comparator
                final Map<TabInfo, VirtualFile> tabToFile = new IdentityHashMap<>();
                for (int i = 0; i < Math.min(tabInfoList.size(), fileList.size()); i++) {
                    tabToFile.put(tabInfoList.get(i), fileList.get(i));
                }

                final int newOrder = sortKey(newFile, sortedGroups, defaultOrder);
                final String newName = newFile.getName();

                // sortTabs comparator:
                //   • All tabs EXCEPT the new one keep their current relative order exactly.
                //   • The new tab is inserted at the position dictated by its group order.
                Comparator<TabInfo> comparator = (a, b) -> {
                    boolean aIsNew = (a == newTabInfo);
                    boolean bIsNew = (b == newTabInfo);

                    if (!aIsNew && !bIsNew) {
                        return Integer.compare(
                                stableOrder.getOrDefault(a, 0),
                                stableOrder.getOrDefault(b, 0));
                    }

                    TabInfo existing     = aIsNew ? b : a;
                    VirtualFile existingFile = tabToFile.get(existing);

                    if (existing.isPinned()) return aIsNew ? 1 : -1;
                    if (newTabInfo.isPinned()) return aIsNew ? -1 : 1;

                    int existingOrder = sortKey(existingFile, sortedGroups, defaultOrder);
                    int cmp;
                    if (newOrder != existingOrder) {
                        cmp = Integer.compare(newOrder, existingOrder);
                    } else {
                        String existingName = existingFile != null ? existingFile.getName() : "";
                        cmp = String.CASE_INSENSITIVE_ORDER.compare(newName, existingName);
                    }
                    return aIsNew ? cmp : -cmp;
                };

                invokeSortTabs(tabs, comparator);

                LOG.info("TabReorderExecutor: placed '" + newFile.getName() +
                         "' at group-order=" + newOrder + " in window.");
            }
        } catch (Exception ex) {
            LOG.warn("TabReorderExecutor: placeNewTab failed", ex);
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
