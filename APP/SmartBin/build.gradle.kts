// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") apply false
    id("org.jetbrains.kotlin.android") apply false
}

// No `buildscript` block needed, as all repository and plugin management is now in `settings.gradle.kts`.

allprojects {
    // Avoid declaring repositories here as repositories are managed in `settings.gradle.kts`

}
