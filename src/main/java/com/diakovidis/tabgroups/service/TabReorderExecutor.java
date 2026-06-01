package com.diakovidis.tabgroups.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.diakovidis.tabgroups.model.TabGroup;
import com.diakovidis.tabgroups.settings.TabGroupsSettings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared logic for reordering tabs. Used by both the right-click action
 * and the "Test" button in Settings.
 * <p>
 * Pinned tabs are never touched — they keep their position and pinned state.
 */
public final class TabReorderExecutor {

    private static final Logger LOG = Logger.getInstance(TabReorderExecutor.class);

    private TabReorderExecutor() {
    }

    /**
     * Reorders all open editor tabs in the given project according to the
     * persisted {@link TabGroupsSettings}.
     *
     * @return the number of unpinned tabs that were reordered, or 0 if nothing happened.
     */
    public static int reorder(Project project) {
        return reorder(project, TabGroupsSettings.getInstance(project).getTabGroups());
    }

    /**
     * Reorders all open (unpinned) editor tabs using the supplied tab-group rules
     * (useful for testing with unsaved / in-progress settings).
     * Pinned tabs are left completely untouched.
     */
    @SuppressWarnings("UnstableApiUsage")
    public static int reorder(Project project, List<TabGroup> tabGroups) {
        if (project == null || project.isDisposed()) {
            return 0;
        }

        FileEditorManager editorManager = FileEditorManager.getInstance(project);
        VirtualFile[] openFiles = editorManager.getOpenFiles();

        if (openFiles.length == 0) {
            LOG.info("TabReorderExecutor: No open tabs to reorder.");
            return 0;
        }

        // ── Identify pinned files ────────────────────────────────────────────
        Set<VirtualFile> pinnedFiles = collectPinnedFiles(project);
        LOG.info("TabReorderExecutor: " + pinnedFiles.size() + " pinned tab(s) will be skipped.");

        // Separate unpinned files (preserve encounter order from getOpenFiles)
        List<VirtualFile> unpinnedFiles = new ArrayList<>();
        for (VirtualFile file : openFiles) {
            if (!pinnedFiles.contains(file)) {
                unpinnedFiles.add(file);
            }
        }

        if (unpinnedFiles.isEmpty()) {
            LOG.info("TabReorderExecutor: All tabs are pinned — nothing to reorder.");
            return 0;
        }

        LOG.info("TabReorderExecutor: Reordering " + unpinnedFiles.size() + " unpinned tab(s).");

        List<VirtualFile> sortedUnpinned = TabSorter.sort(unpinnedFiles, tabGroups);

        // Remember which file had focus (we restore it after reordering)
        VirtualFile selectedFile = editorManager.getSelectedFiles().length > 0
                ? editorManager.getSelectedFiles()[0]
                : null;

        ApplicationManager.getApplication().invokeLater(() -> {
            // Close only unpinned tabs; pinned tabs stay put
            for (VirtualFile file : unpinnedFiles) {
                editorManager.closeFile(file);
            }
            // Reopen unpinned tabs in sorted order
            for (VirtualFile file : sortedUnpinned) {
                editorManager.openFile(file, false);
            }
            // Restore previously active tab
            if (selectedFile != null && selectedFile.isValid()) {
                editorManager.openFile(selectedFile, true);
            }
            LOG.info("TabReorderExecutor: Tabs reordered successfully.");
        });

        return unpinnedFiles.size();
    }

    /**
     * Collects all files whose tab is currently pinned across every editor window.
     */
    @SuppressWarnings("UnstableApiUsage")
    private static Set<VirtualFile> collectPinnedFiles(Project project) {
        Set<VirtualFile> pinned = new HashSet<>();
        try {
            FileEditorManagerEx managerEx = FileEditorManagerEx.getInstanceEx(project);
            for (EditorWindow window : managerEx.getWindows()) {
                for (VirtualFile file : window.getFiles()) {
                    if (window.isFilePinned(file)) {
                        pinned.add(file);
                    }
                }
            }
        } catch (Exception e) {
            // If the internal API changes between IDE versions, degrade gracefully
            // (treat all tabs as unpinned rather than crashing)
            LOG.warn("TabReorderExecutor: Could not detect pinned tabs — all tabs will be reordered.", e);
        }
        return pinned;
    }
}

