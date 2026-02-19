/*
 * Copyright 2020 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("dev.icerock.moko.gradle.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.icerockdev"
    buildFeatures {
        compose = true
    }

    defaultConfig {
        applicationId = "dev.icerock.moko.samples.paging"

        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(libs.activityCompose)
    implementation(platform(libs.composeBom))
    implementation(libs.composeMaterial3)
    implementation(libs.composeUi)
    implementation(libs.composeUiToolingPreview)
    implementation(libs.lifecycleRuntimeCompose)
    implementation(projects.sample.mppLibrary)

    debugImplementation(libs.composeUiTooling)
}
