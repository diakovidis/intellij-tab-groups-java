plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = "com.tabgroups"
version = "1.0.0"

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

