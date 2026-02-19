/*
 * Copyright 2020 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    id("dev.icerock.moko.gradle.multiplatform.mobile")
    id("dev.icerock.moko.gradle.detekt")
}

kotlin {
    jvm()
}

android {
    namespace = "dev.icerock.moko.remotestate"
}

dependencies {
    commonMainImplementation(libs.coroutines)
}
