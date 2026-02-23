@file:JvmName("UtilsAndroidUnitTestKt")

/*
 * Copyright 2020 IceRock MAG Inc. Use of this source code is governed by the Apache 2.0 license.
 */

package dev.icerock.moko.paging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest

actual fun <T> runTest(block: suspend CoroutineScope.() -> T): T {
    var result: Result<T>? = null
    runTest {
        result = runCatching { block() }
    }
    return result?.getOrThrow() ?: error("runTest returned null result")
}
