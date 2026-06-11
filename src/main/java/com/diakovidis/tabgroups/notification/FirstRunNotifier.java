package com.diakovidis.tabgroups.notification;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.diakovidis.tabgroups.settings.TabGroupsConfigurable;
import org.jetbrains.annotations.NotNull;

/**
 * Shows a one-time balloon notification the first time TabOrder automatically
 * sorts tabs in a project. Lets new users know the plugin is active and
 * offers a direct link to the Settings panel.
 *
 * <p>The "shown" flag is stored at the application level
 * ({@link PropertiesComponent#getInstance()}) so it fires at most once per
 * IDE installation, not once per project.
 */
public final class FirstRunNotifier {

    private static final String PROPERTY_KEY = "taborder.v1.firstSortNotificationShown";

    private FirstRunNotifier() {}

    /**
     * Shows the notification if it has not been shown before.
     * Safe to call from any thread — notification posting is always async.
     */
    public static void showIfNeeded(@NotNull Project project) {
        PropertiesComponent props = PropertiesComponent.getInstance();
        if (props.getBoolean(PROPERTY_KEY, false)) return;

        props.setValue(PROPERTY_KEY, true);

        Notification notification = NotificationGroupManager.getInstance()
                .getNotificationGroup("TabOrder")
                .createNotification(
                        "TabOrder is active",
                        "Your tabs were just sorted automatically. Configure groups to match your project structure.",
                        NotificationType.INFORMATION
                );

        notification.addAction(new NotificationAction("Configure Rules") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification n) {
                n.expire();
                ShowSettingsUtil.getInstance().showSettingsDialog(project, TabGroupsConfigurable.class);
            }
        });

        notification.notify(project);
    }
}
