package com.diakovidis.tabgroups.listener;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.diakovidis.tabgroups.service.TabReorderExecutor;
import com.diakovidis.tabgroups.settings.TabGroupsSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Listens for new file-open events and immediately moves the new tab into its
 * correct group position — so every tab snaps to the right place automatically.
 * <p>
 * Only triggers when at least one Tab Group rule is configured.
 * Skips itself when {@link TabReorderExecutor#isReordering()} is {@code true}
 * to prevent infinite loops during fallback close/reopen operations.
 */
public class TabAutoSorterListener implements FileEditorManagerListener {

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        // Avoid re-entering during a close/reopen reorder
        if (TabReorderExecutor.isReordering()) return;

        Project project = source.getProject();
        if (project.isDisposed()) return;

        // Skip auto-sort if no groups are configured
        if (TabGroupsSettings.getInstance(project).getTabGroups().isEmpty()) return;

        // reorder() schedules the actual work via invokeLater so the platform
        // finishes inserting the tab before we sort.
        TabReorderExecutor.reorder(project);
    }
}
