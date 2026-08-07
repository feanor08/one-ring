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

// NOTE: org.jetbrains.kotlinx.binary-compatibility-validator was evaluated for
// this phase and deliberately NOT applied: it registers zero apiDump/apiCheck
// tasks against :one-ring (confirmed with both 0.16.3 and 0.18.1) because it
// hooks into the Kotlin Gradle Plugin's target model, which only exists when
// org.jetbrains.kotlin.android is applied -- and AGP 9's built-in Kotlin
// support (see the comment on the plugins block above) means that plugin
// must NOT be applied here. A plugin that silently registers nothing is
// worse than no plugin: apiCheck would never run in CI while looking like it
// does. Revisit once BCV (or an AGP9-native equivalent) supports AGP's
// built-in Kotlin compilation. Until then, ABI hygiene for the pre-1.0
// surface is enforced manually: config classes are @Immutable data classes
// with an internal constructor + @ConsistentCopyVisibility (so copy()/
// component() can't leak a public surface the constructor was meant to
// hide), constructed only via OneRingDefaults factories.

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
