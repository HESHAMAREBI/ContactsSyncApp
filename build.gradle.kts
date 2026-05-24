// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.kapt) apply false
}

// Force Kotlin stdlib to the project Kotlin version across all modules.
// This is a controlled, project-level constraint to avoid accidental pulls of Kotlin 2.x
// from transitive dependencies while we stabilize library versions.
// Keep this in sync with `gradle/libs.versions.toml` kotlin = "1.9.23".
// NOTE: Kotlin stdlib constraints moved to `settings.gradle.kts` as declarative dependency constraints.

// Apply declarative dependency constraints to all subprojects so we don't rely on
// resolutionStrategy.force. This ensures consistent Kotlin stdlib versions while
// keeping the configuration declarative and visible to Gradle's constraints model.
// Force Kotlin stdlib to the project Kotlin version across all modules.
// This is a controlled, project-level constraint to avoid accidental pulls of Kotlin 2.x
// from transitive dependencies while we stabilize library versions.
// Keep this in sync with `gradle/libs.versions.toml` kotlin = "1.9.23".
configurations.all {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlin:kotlin-stdlib:1.9.23",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.23",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.23"
        )
    }
}