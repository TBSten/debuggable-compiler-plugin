package me.tbsten.debuggable.runtime.diagram

import me.tbsten.debuggable.runtime.annotations.InternalDebuggableApi
import me.tbsten.debuggable.runtime.logging.DebugLogger
import me.tbsten.debuggable.runtime.logging.DefaultDebugLogger

/**
 * Represents one captured sub-expression from a method argument.
 * The compiler inserts code that evaluates each leaf variable/field and records it here.
 */
@InternalDebuggableApi
data class DiagramCapture(
    val name: String,
    val value: Any?,
)

/**
 * Builds a diagram log line of the form:
 *   `functionName(argExpr)  // name1=val1, name2=val2`
 *
 * If captures is empty the inline comment is omitted.
 */
@InternalDebuggableApi
fun buildDiagramString(
    functionName: String,
    argExpr: String,
    vararg captures: DiagramCapture,
): String {
    val call = "$functionName($argExpr)"
    if (captures.isEmpty()) return call
    val annotations = captures.joinToString(", ") { "${it.name}=${valueToString(it.value)}" }
    return "$call  // $annotations"
}

@InternalDebuggableApi
fun logDiagram(
    functionName: String,
    argExpr: String,
    vararg captures: DiagramCapture,
    logger: DebugLogger = DefaultDebugLogger,
) {
    logger.log(buildDiagramString(functionName, argExpr, *captures))
}

private fun valueToString(value: Any?): String = try {
    value?.toString() ?: "null"
} catch (t: Throwable) {
    "<toString threw ${t::class.simpleName ?: "Throwable"}>"
}
