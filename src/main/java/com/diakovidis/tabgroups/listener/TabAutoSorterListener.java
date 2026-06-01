package com.diakovidis.tabgroups.listener;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.diakovidis.tabgroups.service.TabReorderExecutor;
import com.diakovidis.tabgroups.settings.TabGroupsSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Listens for new file-open events and immediately moves the new tab into its
 * correct group position — so every tab snaps to the right place automatically,
 * without any user action.
 * <p>
 * Only triggers when at least one Tab Group rule is configured; does nothing
 * when no groups are defined (preventing unwanted alphabetical sorting).
 */
public class TabAutoSorterListener implements FileEditorManagerListener {

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        Project project = source.getProject();
        if (project.isDisposed()) return;

        // Skip auto-sort if the user hasn't configured any groups yet
        if (TabGroupsSettings.getInstance(project).getTabGroups().isEmpty()) return;

        // Defer one EDT cycle so the platform finishes inserting the tab
        // before we call sortTabs() on the underlying JBTabsImpl.
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!project.isDisposed()) {
                TabReorderExecutor.reorder(project);
            }
        });
    }
}

