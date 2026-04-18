package me.tbsten.debuggable.compiler.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

/**
 * Wires Debuggable-specific FIR checkers into the compiler's analysis phase.
 *
 * B-6 introduces the plumbing only; actual checkers are added in B-7.
 */
class DebuggableFirAdditionalCheckersExtension(
    session: FirSession,
) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        // TODO(B-7 FIR): populate with DebuggableLoggerChecker once the K2.3.20 FIR
        //                diagnostic API stabilises. For now, validation is performed
        //                in DebuggableClassTransformer (IR phase).
    }
}
