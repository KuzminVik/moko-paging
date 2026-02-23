/*
 * Copyright 2020 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("dev.icerock.moko.gradle.multiplatform.mobile")
    id("dev.icerock.moko.gradle.publication")
    id("dev.icerock.moko.gradle.detekt")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvm()
}

android {
    namespace = "dev.icerock.moko.paging.compose"
    
    compileSdk = 35
    
    defaultConfig {
        minSdk = 21
    }
}

dependencies {
    commonMainApi(projects.paging)
    implementation(libs.composeMaterial3)
    androidMainImplementation(platform(libs.composeBom))
    androidMainImplementation(libs.composeUi)
}
