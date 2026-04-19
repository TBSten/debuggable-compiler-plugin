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
 *
 * ## Public-SPI stability contract
 *
 * This interface is the narrow boundary between the main plugin JAR (which only compiles
 * against version-agnostic APIs) and each `debuggable-compiler-compat-kXX` shadow. Third-party
 * compat implementations could legitimately register themselves via `ServiceLoader` on a
 * user's classpath, though we don't recommend it — we treat this file as **public SPI** for
 * backward-compatibility purposes:
 *
 * - New methods MUST have a default body, never an abstract declaration.
 * - [Options] gains new fields only with a default value so older compat impls keep linking.
 * - [Factory.minVersion] is parsed with [SimpleKotlinVersion]; formats it has accepted historically
 *   (e.g. `"2.3.20"`, `"2.4.0-Beta1"`, `"2.3.20-dev-5706"`) remain accepted.
 * - Renaming or removing anything here is a breaking change and requires a minor version bump.
 *
 * ABI drift on this SPI is caught by `binary-compatibility-validator` — see the
 * generated `compat.api` baseline under each per-version compat module's `api/` directory.
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
