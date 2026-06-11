# Changelog

All notable changes to **Tab Order** are documented here.  
This project follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- Nothing yet.

---

## [2.0.0] — 2026-06-11

### Added
- **Keyboard shortcut** `Ctrl+Shift+Alt+O` — trigger **Order Tabs** without touching the mouse.
- **Enable / disable groups** — each group in the settings table now has a checkbox; unchecked groups are completely skipped during sorting and matching without being deleted.
- **8 built-in presets** — instantly load a curated group configuration for the most popular project types: Java/Maven, Java/Spring Boot, Java+Angular (full-stack), Angular/TypeScript, Python/Django, PHP/Laravel, Go, and Kotlin/Android. Load via the **Load Preset** toolbar button in Settings.
- **First-run notification** — a balloon notification appears the first time the plugin is activated, pointing users to Settings → Tools → TabOrder.

### Improved
- Plugin compatibility widened from IntelliJ **2025.1+** to **2023.1+** (`since-build` changed from `251` → `231`).

## [1.1.3] — 2026-06-05

### Added
- **Auto-sort on file open**: newly opened tabs are automatically placed at their correct group position without any user action.
- **Settings panel** redesigned: tab groups are now displayed in a two-column table (Order | Name) instead of a plain list.
- Table is always sorted by the **Order** column for clarity.
- New groups and duplicated groups are automatically assigned the next available order number (placed at the bottom).
- Move Up / Move Down correctly swaps both list position and order values between adjacent groups.
- Added Icons for light and dark themes.

### Improved 
- Tab sorting now uses the same mechanism as drag-and-drop — resulting in **zero flicker** and preserved editor scroll positions (no more close/reopen).


## [1.1.2] — 2026-06-04

### Fixed
- Drag-and-drop between tab rows in multi-row tab mode no longer triggers a re-sort when the drag takes more than one EDT cycle. Replaced `invokeLater`-based detection with a **10-second timestamp window**: any file closed within 10 seconds that immediately reopens is treated as a D&D move and placement is skipped.

### Improved
- Release changelog notes are now **automatically extracted from `CHANGELOG.md`** at build time and injected into the JetBrains Marketplace "What's new" field and the in-IDE plugin update dialog — no manual copy-paste needed for future releases.
- GitHub Release body is also automatically populated from the matching `CHANGELOG.md` section via the CI workflow.

---

## [1.1.1] — 2026-06-04

### Improved
- **Settings panel** redesigned: tab groups are now displayed in a two-column table (Order | Name) instead of a plain list.
- Table is always sorted by the **Order** column for clarity.
- **Order values** are now restricted to 0 and positive integers only.
- New groups and duplicated groups are automatically assigned the next available order number (placed at the bottom).
- Move Up / Move Down correctly swaps both list position and order values between adjacent groups.

### Fixed
- Moving a group up or down in settings no longer briefly flashes the old value before updating.

---

## [1.1.0] — 2026-06-03

### Added
- **Auto-sort on file open**: newly opened tabs are automatically placed at their correct group position without any user action.
- Plugin icon (`pluginIcon.svg` / `pluginIcon_dark.svg`) added for light and dark themes.
- Cross-IDE compatibility: removed dependency on `com.intellij.java`, the plugin now works in **all JetBrains IDEs** (PhpStorm, PyCharm, WebStorm, Rider, GoLand, etc.).

### Improved
- Tab sorting now uses `JBTabsImpl.sortTabs()` — the same mechanism as drag-and-drop — resulting in **zero flicker** and preserved editor scroll positions (no more close/reopen).
- Newly opened tabs are placed in-place without disturbing other tabs.

### Fixed
- Drag-and-drop between tab rows in multi-row tab mode no longer triggers an unwanted re-sort that reverts the user's manual arrangement.
- Deprecated IntelliJ APIs replaced throughout (`getOpenFiles`, `getSelectedFiles`, `openFile`, `getFiles`, `window.getFiles`).

---

## [1.0.0] — 2026-06-01

### Added
- **Group and Sort Tabs** action in the editor tab context menu.
- Named tab groups with regex-based file matching (regex and glob syntax both supported).
- Configurable sort order per group.
- Import / Export of group configurations to/from JSON.
- Per-project persistence via `.idea/tabGroups.xml`.
- Pinned tabs are never moved.
- Compatible with IntelliJ IDEA 2025.1 and all future versions.

---

[Unreleased]: https://github.com/diakovidis/intellij-tab-groups-java/compare/v2.0.0...HEAD
[2.0.0]: https://github.com/diakovidis/intellij-tab-groups-java/compare/v1.1.3...v2.0.0
[1.1.3]: https://github.com/diakovidis/intellij-tab-groups-java/compare/v1.1.2...v1.1.3
[1.1.2]: https://github.com/diakovidis/intellij-tab-groups-java/compare/v1.1.1...v1.1.2
[1.1.1]: https://github.com/diakovidis/intellij-tab-groups-java/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/diakovidis/intellij-tab-groups-java/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/diakovidis/intellij-tab-groups-java/releases/tag/v1.0.0

