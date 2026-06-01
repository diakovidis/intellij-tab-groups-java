package com.diakovidis.tabgroups.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.diakovidis.tabgroups.service.TabReorderExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * Action that reorders all currently open editor tabs based on Tab Groups rules.
 * Available from the editor tab right-click popup menu as "Reorder Tabs".
 */
public class ReorderTabsAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            TabReorderExecutor.reorder(project);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        boolean enabled = project != null
                && FileEditorManager.getInstance(project).getOpenFiles().length > 0;
        e.getPresentation().setEnabledAndVisible(enabled);
    }
}
