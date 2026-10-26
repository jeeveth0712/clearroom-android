// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        // AGP 9's built-in Kotlin defaults to KGP 2.2.10; the AGP 9 release notes say to raise it
        // here. Must match the Compose compiler plugin version (both use libs.versions.kotlin).
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
