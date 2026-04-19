# Consumer R8/ProGuard rules for debuggable-runtime.
#
# Packaged into the Android AAR under META-INF/proguard/ so consumer modules
# automatically pick them up with no extra configuration.
#
# These rules matter because the compiler plugin injects references to runtime
# symbols (loggers, registry, extension functions) into user code, and some of
# those references are only visible at the bytecode level — R8's tree shaker
# doesn't see the compile-time wiring and would otherwise strip them.

# Extension functions and the registry factory are called from generated IR.
-keep class me.tbsten.debuggable.runtime.extensions.StateExtensionsKt {
    public static *** debuggableState(...);
}
-keep class me.tbsten.debuggable.runtime.extensions.FlowExtensionsKt {
    public static *** debuggableFlow(...);
}
-keep class me.tbsten.debuggable.runtime.registry.DebugCleanupRegistryKt {
    public static *** DebugCleanupRegistry();
}

# User-supplied `logger = MyLogger::class` targets are referenced from IR by
# FQN. Without this, R8 may rename `INSTANCE` or the class itself and the
# injected `getstatic` becomes a NoSuchFieldError at runtime.
-keep,allowobfuscation class * implements me.tbsten.debuggable.runtime.logging.DebugLogger {
    public static *** INSTANCE;
}

# Default process-wide logger swap and the singleton registry default.
-keep class me.tbsten.debuggable.runtime.logging.DefaultDebugLogger { *; }
-keep class me.tbsten.debuggable.runtime.registry.DebugCleanupRegistry$Default { *; }

# Annotations are consumed at compile time, but @Debuggable is CLASS-retention
# to help the checker and IR detection; keep them so reflection-based tools
# (and future FIR lookups via bytecode) still see them.
-keepattributes *Annotation*
-keep @me.tbsten.debuggable.runtime.annotations.Debuggable class *
