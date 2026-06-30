/*
 * Copyright 2020 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
    plugins {
        id("org.jetbrains.kotlin.plugin.compose") version "2.1.10"
    }
}

plugins {
    id("com.gradle.enterprise") version "3.10.1"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

gradleEnterprise {
    buildScan {
        publishAlwaysIf(System.getenv("CI").isNullOrEmpty().not())
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}

include(":paging")
include(":paging-compose")
include(":sample:android-app")
include(":sample:mpp-library")
