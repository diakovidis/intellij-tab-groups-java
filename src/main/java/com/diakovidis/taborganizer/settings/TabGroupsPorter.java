package com.diakovidis.taborganizer.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.diakovidis.taborganizer.model.TabGroup;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles JSON export and import of Tab Organizer tab-group settings.
 *
 * <p>File format (pretty-printed JSON):</p>
 * <pre>
 * {
 *   "version": 1,
 *   "tabGroups": [
 *     { "name": "Tests",    "order": 1, "regex": ".*Test\\.java" },
 *     { "name": "Services", "order": 2, "regex": ".&#42;/service/.*" }
 *   ]
 * }
 * </pre>
 */
public final class TabGroupsPorter {

    private static final int FORMAT_VERSION = 1;
    private static final String FILE_EXT  = "json";
    private static final String FILE_DESC = "Tab Organizer Settings (*.json)";
    private static final String DEFAULT_FILENAME = "tab-organizer-settings.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private TabGroupsPorter() {}

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Shows a save-file dialog and writes the given tab groups to a JSON file.
     *
     * @param groups the tab groups to export
     * @param parent parent component for dialogs
     * @return {@code true} if the export succeeded
     */
    public static boolean export(List<TabGroup> groups, Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Tab Organizer Settings");
        chooser.setFileFilter(new FileNameExtensionFilter(FILE_DESC, FILE_EXT));
        chooser.setSelectedFile(new File(DEFAULT_FILENAME));

        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return false;
        }

        File file = ensureExtension(chooser.getSelectedFile());

        // Warn before overwriting an existing file
        if (file.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(parent,
                    "File already exists:\n" + file.getAbsolutePath() +
                    "\n\nDo you want to overwrite it?",
                    "Confirm Overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (overwrite != JOptionPane.YES_OPTION) {
                return false;
            }
        }

        try {
            String json = serialize(groups);
            Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(parent,
                    "Exported " + groups.size() + " tab group(s) to:\n" + file.getAbsolutePath(),
                    "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent,
                    "Failed to write file:\n" + ex.getMessage(),
                    "Export Failed", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Shows an open-file dialog, asks the user to confirm overwriting the current
     * configuration, then reads and returns the tab groups from the selected JSON file.
     *
     * @param parent parent component for dialogs
     * @return the parsed list of tab groups, or {@code null} if cancelled or an error occurred
     */
    public static List<TabGroup> importGroups(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Tab Organizer Settings");
        chooser.setFileFilter(new FileNameExtensionFilter(FILE_DESC, FILE_EXT));

        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        // Confirmation: current config will be overwritten
        int confirm = JOptionPane.showConfirmDialog(parent,
                "<html><b>Importing will replace your current Tab Organizer configuration.</b><br><br>" +
                "All existing tab groups will be removed and replaced with the groups<br>" +
                "from the selected file. This cannot be undone.<br><br>" +
                "Do you want to continue?</html>",
                "Confirm Import", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) {
            return null;
        }

        File file = chooser.getSelectedFile();
        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            List<TabGroup> groups = deserialize(json);
            JOptionPane.showMessageDialog(parent,
                    "Successfully imported " + groups.size() + " tab group(s).",
                    "Import Successful", JOptionPane.INFORMATION_MESSAGE);
            return groups;
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent,
                    "Failed to read file:\n" + ex.getMessage(),
                    "Import Failed", JOptionPane.ERROR_MESSAGE);
            return null;
        } catch (JsonSyntaxException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(parent,
                    "Invalid or unrecognised settings file:\n" + ex.getMessage(),
                    "Import Failed", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private static String serialize(List<TabGroup> groups) {
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);

        JsonArray array = new JsonArray();
        for (TabGroup g : groups) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name",  g.getName());
            entry.addProperty("order", g.getOrder());
            entry.addProperty("regex", g.getRegex());
            array.add(entry);
        }
        root.add("tabGroups", array);

        return GSON.toJson(root);
    }

    private static List<TabGroup> deserialize(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        if (!root.has("tabGroups")) {
            throw new IllegalArgumentException("Missing 'tabGroups' key — not a valid Tab Organizer settings file.");
        }

        JsonArray array = root.getAsJsonArray("tabGroups");
        List<TabGroup> result = new ArrayList<>();

        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            String name  = obj.has("name")  ? obj.get("name").getAsString()  : "";
            int    order = obj.has("order") ? obj.get("order").getAsInt()    : 0;
            String regex = obj.has("regex") ? obj.get("regex").getAsString() : "";
            result.add(new TabGroup(name, order, regex));
        }

        return result;
    }

    private static File ensureExtension(File file) {
        if (!file.getName().toLowerCase().endsWith("." + FILE_EXT)) {
            return new File(file.getAbsolutePath() + "." + FILE_EXT);
        }
        return file;
    }
}


