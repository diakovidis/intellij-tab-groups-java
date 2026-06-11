# TabOrder — IntelliJ Plugin

[![Build](https://github.com/diakovidis/intellij-tab-groups-java/actions/workflows/build.yml/badge.svg)](https://github.com/diakovidis/intellij-tab-groups-java/actions/workflows/build.yml)
[![Version](https://img.shields.io/badge/version-1.1.3-blue)](https://github.com/diakovidis/intellij-tab-groups-java/releases)
[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ-2023.1%2B-orange)](https://plugins.jetbrains.com/)
[![JetBrains Marketplace](https://img.shields.io/badge/JetBrains-Marketplace-green?logo=jetbrains)](https://plugins.jetbrains.com/plugin/com.tabgroups.intellij-tab-groups-java)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> Sort your IntelliJ editor tabs into named groups using regex-based rules — with per-project persistence.

---

## ✨ Features

- **Named tab groups** — define as many groups as you need, each with a display name and sort order.
- **Regex matching** — each group targets files whose paths match a regular expression (e.g. `.*Test\.java$` for test files).
- **One-click reorder** — right-click any editor tab and choose **Order Tabs** to instantly sort all open, unpinned tabs.
- **Auto-sort on open** — newly opened tabs are automatically placed at their correct group position.
- **Per-project settings** — groups are stored in `.idea/tabGroups.xml` and travel with your project.
- **Import / Export** — share group configurations across machines or team members via JSON.
- Works in **all JetBrains IDEs** — IntelliJ IDEA, PhpStorm, PyCharm, WebStorm, Rider, GoLand, RubyMine, DataGrip, CLion and more.

---

## 🖼️ Screenshots

### ⚡ Auto-sort in action

![Tab sorting demo](src/main/resources/sort%20tabs.gif)

> Tabs snap into their configured group order the moment a new file is opened — no manual dragging required.

### ⚙️ Settings panel

![Settings panel](src/main/resources/settings.jpg)

> Define groups with a name, sort order, and regex pattern. Enable/disable individual rules, load a built-in preset, or import/export via JSON.

---

## 🚀 Installation

### From the JetBrains Marketplace

1. Open **Settings → Plugins → Marketplace**.
2. Search for **TabOrder**.
3. Click **Install** and restart the IDE.

### Manual installation

1. Download the latest `.zip` from the [Releases](https://github.com/diakovidis/intellij-tab-groups-java/releases) page.
2. Open **Settings → Plugins → ⚙ → Install Plugin from Disk…**
3. Select the downloaded `.zip` and restart the IDE.

---

## 🛠️ Usage

### Configure groups

1. Go to **Settings → Tools → TabOrder**.
2. Click **+** to add a new group.
3. Fill in:
   | Field | Description |
   |-------|-------------|
   | **Name** | Label shown for the group (e.g. `Tests`) |
   | **Order** | Sort priority — lower numbers appear first |
   | **Regex** | File-path pattern to match (e.g. `.*Test\.java$`) |
4. Click **Apply / OK**.

### Order Tabs

Right-click any editor tab → **Order Tabs**.  
Tabs are sorted by group order; files not matching any group are placed at the end.

---

## 🏗️ Building from source

**Requirements:** JDK 21+, internet access (Gradle downloads the IDE).

```bash
# Clone
git clone https://github.com/diakovidis/intellij-tab-groups-java.git
cd intellij-tab-groups-java

# Build the plugin ZIP
./gradlew buildPlugin

# Run the plugin inside a sandboxed IDE instance
./gradlew runIde

# Build against a specific IntelliJ version
./gradlew buildPlugin -PplatformVersion=2026.1
```

The distributable ZIP is generated at `build/distributions/`.

---

## 🧪 Running tests

```bash
./gradlew test
```

---

## 📁 Project structure

```
src/main/java/com/diakovidis/tabgroups/
├── action/          # ReorderTabsAction — entry point from the tab context menu
├── listener/        # TabAutoSorterListener — auto-sorts on file open
├── model/           # TabGroup — domain model
├── service/         # TabGroupMatcher, TabReorderExecutor, TabSorter — core logic
└── settings/        # TabGroupsConfigurable, TabGroupsSettings, TabGroupsSettingsPanel, TabGroupsPorter
```

---

## 🤝 Contributing

1. Fork the repository.
2. Create a feature branch: `git checkout -b feature/my-feature`.
3. Commit your changes: `git commit -m "feat: my feature"`.
4. Push and open a Pull Request.

Please make sure `./gradlew buildPlugin` succeeds before submitting a PR.

---

## 📄 License

[MIT](LICENSE) © diakovidis
