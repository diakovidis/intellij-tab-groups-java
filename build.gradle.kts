plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = "com.taborganizer"
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
        intellijIdea(platformVersion)
        bundledPlugin("com.intellij.java")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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
}

