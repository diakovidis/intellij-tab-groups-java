package com.diakovidis.taborganizer.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.diakovidis.taborganizer.model.TabGroup;
import com.diakovidis.taborganizer.settings.TabOrganizerSettings;

import java.util.Arrays;
import java.util.List;

/**
 * Shared logic for reordering tabs. Used by both the right-click action
 * and the "Test" button in Settings.
 */
public final class TabReorderExecutor {

    private static final Logger LOG = Logger.getInstance(TabReorderExecutor.class);

    private TabReorderExecutor() {
    }

    /**
     * Reorders all open editor tabs in the given project according to the
     * persisted {@link TabOrganizerSettings}.
     *
     * @return the number of tabs that were reordered, or 0 if nothing happened.
     */
    public static int reorder(Project project) {
        return reorder(project, TabOrganizerSettings.getInstance(project).getTabGroups());
    }

    /**
     * Reorders all open editor tabs using the supplied tab-group rules
     * (useful for testing with unsaved / in-progress settings).
     */
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

        LOG.info("TabReorderExecutor: Reordering " + openFiles.length + " open tab(s).");

        List<VirtualFile> sortedFiles = TabSorter.sort(Arrays.asList(openFiles), tabGroups);

        VirtualFile selectedFile = editorManager.getSelectedFiles().length > 0
                ? editorManager.getSelectedFiles()[0]
                : null;

        ApplicationManager.getApplication().invokeLater(() -> {
            for (VirtualFile file : openFiles) {
                editorManager.closeFile(file);
            }
            for (VirtualFile file : sortedFiles) {
                editorManager.openFile(file);
            }
            if (selectedFile != null && selectedFile.isValid()) {
                editorManager.openFile(selectedFile);
            }
            LOG.info("TabReorderExecutor: Tabs reordered successfully.");
        });

        return openFiles.length;
    }
}

