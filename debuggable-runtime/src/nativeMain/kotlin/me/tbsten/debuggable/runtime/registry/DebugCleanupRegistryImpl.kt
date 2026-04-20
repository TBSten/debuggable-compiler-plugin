package me.tbsten.debuggable.runtime.registry

import me.tbsten.debuggable.runtime.annotations.InternalDebuggableApi

@InternalDebuggableApi
actual fun DebugCleanupRegistry(): DebugCleanupRegistry = DebugCleanupRegistryCommonImpl()
