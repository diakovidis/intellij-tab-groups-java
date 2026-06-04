package com.diakovidis.tabgroups.listener;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.diakovidis.tabgroups.model.TabGroup;
import com.diakovidis.tabgroups.service.TabReorderExecutor;
import com.diakovidis.tabgroups.settings.TabGroupsSettings;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Listens for file-open events and moves ONLY the newly-opened tab into its
 * correct group position — all other tabs are left exactly where they are.
 *
 * <h3>Drag-and-drop detection (multi-row tab mode)</h3>
 * A D&amp;D between rows produces exactly this event sequence:
 * <pre>
 *   fileClosed(A)  →  fileOpened(A)
 * </pre>
 * A genuine new-file open produces only {@code fileOpened} with no preceding
 * {@code fileClosed} for the same file.  A user closing a tab and later
 * reopening a different file produces:
 * <pre>
 *   fileClosed(A)  →  fileOpened(B)   (B ≠ A)
 * </pre>
 *
 * <p>We exploit this by maintaining {@code closedSinceLastOpen} — the set of
 * files that have been closed since the last {@code fileOpened} event.
 * <ul>
 *   <li>On {@code fileClosed}: add the file to the set.</li>
 *   <li>On {@code fileOpened}:
 *     <ol>
 *       <li>Check if the opened file is in the set → D&amp;D → skip placement.</li>
 *       <li>Clear the entire set — any other closes that happened before this
 *           open are now "stale" (a different file was opened in between,
 *           which means those closes were not part of a D&amp;D for this event).</li>
 *       <li>If not a D&amp;D, place the new tab.</li>
 *     </ol>
 *   </li>
 * </ul>
 *
 * <p>No timers, no EDT scheduling — pure event-sequence logic.
 *
 * <p>Reordering is also skipped when:
 * <ul>
 *   <li>No tab groups are configured for the project.</li>
 *   <li>A fallback close/reopen reorder is already in progress.</li>
 * </ul>
 */
public class TabAutoSorterListener implements FileEditorManagerListener {

    /**
     * Files closed since the last {@code fileOpened} event.
     * If the same file appears in {@code fileOpened}, it is a D&amp;D row-move.
     * Cleared on every {@code fileOpened} so stale close entries never linger.
     */
    private final Set<VirtualFile> closedSinceLastOpen = new HashSet<>();

    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        closedSinceLastOpen.add(file);
    }

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        // Check before clearing — was this specific file just closed (D&D)?
        boolean wasDragAndDrop = closedSinceLastOpen.contains(file);

        // Always clear: other pending closes are stale now that a new open has fired.
        closedSinceLastOpen.clear();

        if (wasDragAndDrop) return; // D&D row-move — preserve the user's arrangement

        // Skip if a fallback close/reopen is already running (prevents infinite loop).
        if (TabReorderExecutor.isReordering()) return;

        Project project = source.getProject();
        if (project.isDisposed()) return;

        // Skip if no groups are configured.
        List<TabGroup> groups = TabGroupsSettings.getInstance(project).getTabGroups();
        if (groups.isEmpty()) return;

        // Place ONLY the new tab; all other tabs keep their current positions.
        TabReorderExecutor.placeNewTab(project, file, groups);
    }
}
