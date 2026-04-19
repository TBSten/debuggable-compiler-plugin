package me.tbsten.debuggable.compiler.compat

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

/**
 * Version-specific implementation of the Debuggable IR transformation.
 *
 * The core plugin (`debuggable-compiler`) delegates `IrGenerationExtension.generate(...)` to
 * whichever [IrInjector] [Factory] matches the Kotlin compiler version on the classpath.
 * Each impl module (`debuggable-compiler-compat-k21`, `...-k23`, etc.) compiles against its
 * own `kotlin-compiler-embeddable` version, so its bytecode only references APIs that exist
 * in that version. The [Factory.minVersion] declared in each module's
 * `META-INF/services` entry determines which impl is loaded at runtime.
 */
interface IrInjector {
    fun transform(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        options: Options,
    )

    /**
     * Feature toggles + logger FQN forwarded from the Gradle DSL.
     *
     * Kept in this interface module to avoid the plugin module depending on any impl.
     */
    data class Options(
        val observeFlow: Boolean = true,
        val logAction: Boolean = true,
        val defaultLoggerFqn: String = "",
    )

    interface Factory {
        /** Lowest Kotlin version (inclusive) that this impl can handle, e.g. `"2.2.0"`. */
        val minVersion: String

        fun create(): IrInjector
    }
}
