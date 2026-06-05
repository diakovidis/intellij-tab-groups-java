plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = "com.tabgroups"
version = "1.1.3"

/**
 * Reads CHANGELOG.md and extracts the section for the current [version],
 * converting Keep-a-Changelog markdown into the basic HTML that the
 * JetBrains Marketplace accepts for "What's new".
 *
 * Format expected:
 *   ## [1.2.3] — YYYY-MM-DD
 *   ### Added
 *   - Foo
 *   ### Fixed
 *   - Bar
 *
 * Output: <b>Added</b><ul><li>Foo</li></ul><b>Fixed</b><ul><li>Bar</li></ul>
 *
 * If the section is not found, a fallback message is returned so the build never fails.
 */
fun extractChangeNotes(): String {
    val ver = project.version.toString()
    val changelog = rootProject.file("CHANGELOG.md").takeIf { it.exists() }?.readText()
        ?: return "See CHANGELOG.md for details."

    val sectionStart = changelog.indexOf("## [$ver]").takeIf { it >= 0 }
        ?: return "See CHANGELOG.md for version $ver details."

    val contentStart = changelog.indexOf('\n', sectionStart) + 1
    val nextSection  = changelog.indexOf("\n## [", contentStart)
    val raw = if (nextSection > 0) changelog.substring(contentStart, nextSection)
              else                  changelog.substring(contentStart)

    val sb = StringBuilder()
    var inList = false

    fun closeList() { if (inList) { sb.append("</ul>"); inList = false } }

    for (line in raw.lines()) {
        when {
            line.startsWith("### ") -> {
                closeList()
                sb.append("<b>${line.removePrefix("### ").trim()}</b>")
            }
            line.startsWith("- ") -> {
                if (!inList) { sb.append("<ul>"); inList = true }
                // preserve **bold** markers → <b>…</b>
                val text = line.removePrefix("- ").trim()
                    .replace(Regex("""\*\*(.+?)\*\*"""), "<b>$1</b>")
                sb.append("<li>$text</li>")
            }
            line.isBlank() -> { /* skip blank lines */ }
            else -> {
                closeList()
                sb.append("<p>${line.trim()}</p>")
            }
        }
    }
    closeList()
    return sb.toString()
}

// Set via -PplatformVersion=2026.1 on the command line, or edit the default here.
// Build against the OLDEST version you want to support for broadest compatibility.
val platformVersion: String by project.extra { project.findProperty("platformVersion") as String? ?: "2025.1" }

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea(platformVersion)   // covers IC and IU; ideaIC was dropped after 2025.2
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // 251 = 2025.1, leaving untilBuild open means "all future versions"
            sinceBuild = "251"
            untilBuild = provider { null }   // no upper limit → works on 2025 and 2026
        }
        // Automatically populated from CHANGELOG.md for every release.
        // Shown on the JetBrains Marketplace "What's new" tab and in the IDE's plugin update dialog.
        changeNotes.set(provider { extractChangeNotes() })
    }

    pluginVerification {
        ides {
            recommended()   // verifies against the IDE versions JetBrains recommends for the plugin's since/until range
        }
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // "default" = stable channel; use "beta" or "eap" for pre-releases
        channels = listOf(if (version.toString().contains("-beta")) "beta" else "default")
    }
}

// Skip verifyPlugin locally — only run it in CI (GitHub Actions sets CI=true automatically)
tasks.named("verifyPlugin") {
    onlyIf { System.getenv("CI") == "true" }
}

