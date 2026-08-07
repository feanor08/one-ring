// Top-level build file; applies no plugins to the root project itself.
//
// AGP 9+ bundles Kotlin compilation support directly, so the standalone
// org.jetbrains.kotlin.android plugin must NOT be applied alongside it
// (see https://issuetracker.google.com/438678642). The Compose compiler
// plugin is still applied separately per module since it is a distinct
// compiler plugin, not part of base Kotlin/Android support.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt")
        // @Composable functions are PascalCase by convention (they read as UI elements).
        ktlint().editorConfigOverride(mapOf("ktlint_function_naming_ignore_when_annotated_with" to "Composable"))
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**/*.kts")
        ktlint()
        trimTrailingWhitespace()
        endWithNewline()
    }
}
