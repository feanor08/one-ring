import com.vanniktech.maven.publish.AndroidSingleVariantLibrary

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dokka)
    alias(libs.plugins.maven.publish)
}

kotlin {
    explicitApi()
}

dokka {
    // Dokka's Android-extension auto-detection ("AndroidExtensionWrapper could
    // not get Android Extension") fails on this AGP-9-built-in-Kotlin module,
    // the same underlying gap as binary-compatibility-validator's (see the
    // comment on the root build.gradle.kts). Pointing the source set at
    // src/main/kotlin directly sidesteps auto-detection entirely.
    dokkaSourceSets.create("main") {
        sourceRoots.from("src/main/kotlin")
    }
    moduleName.set("one-ring")
}

android {
    namespace = "com.feanor08.onering"
    compileSdk = 37

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = false
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Publishing (Phase 10 of docs/PLAN.md): local-only for now.
//
// PLACEHOLDER coordinates -- io.github.feanor08 (a GitHub-account namespace,
// the low-friction path to Maven Central verification later) was named as a
// placeholder in the original design discussion; com.feanor08.onering is
// used consistently as this module's actual Kotlin package/namespace, so
// that's what's published here. Revisit the group id before ever publishing
// somewhere other than mavenLocal.
//
// Deliberately NOT called: publishToMavenCentral() / signAllPublications().
// Central requires a verified namespace and a GPG key neither of which exist
// yet -- see docs/PLAN.md section 20's "v1 vs deferred" table. What IS set up
// here (POM metadata, sources jar, `./gradlew publishToMavenLocal`) is
// exactly what that table calls for now.
mavenPublishing {
    coordinates(
        groupId = "com.feanor08.onering",
        artifactId = "one-ring",
        version = project.findProperty("VERSION_NAME") as? String ?: "0.1.0-SNAPSHOT",
    )

    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = false, // Dokka's javadoc-jar wiring is a follow-up, not yet verified end-to-end.
        ),
    )

    pom {
        name.set("one-ring")
        description.set(
            "A native Android (Kotlin + Jetpack Compose) radial interaction framework -- " +
                "press, scrub, release-to-commit. Domain-neutral: automotive controls, quick-action " +
                "wheels, media controls, tool palettes, and nested radial menus.",
        )
        url.set("https://github.com/feanor08/one-ring") // PLACEHOLDER: no repository exists yet.
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("feanor08")
                name.set("feanor08")
            }
        }
        scm {
            url.set("https://github.com/feanor08/one-ring") // PLACEHOLDER
        }
    }
}
