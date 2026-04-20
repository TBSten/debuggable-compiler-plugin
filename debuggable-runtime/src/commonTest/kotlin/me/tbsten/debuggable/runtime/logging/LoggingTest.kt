@file:OptIn(me.tbsten.debuggable.runtime.annotations.InternalDebuggableApi::class)

package me.tbsten.debuggable.runtime.logging

import kotlin.test.Test

class LoggingTest {

    @Test
    fun `logAction does not throw with no args`() {
        logAction(null, "MyClass.myMethod")
    }

    @Test
    fun `logAction does not throw with multiple args`() {
        logAction(null, "SearchViewModel.onSearchClicked", "query", 42, null)
    }
}
