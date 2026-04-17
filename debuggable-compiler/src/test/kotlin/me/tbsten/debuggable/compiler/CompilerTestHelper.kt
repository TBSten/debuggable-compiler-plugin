package me.tbsten.debuggable.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import java.io.ByteArrayOutputStream
import java.io.PrintStream

abstract class CompilerTestBase {

    protected fun compile(source: String): JvmCompilationResult =
        compile(SourceFile.kotlin("Main.kt", source))

    protected fun compile(vararg sources: SourceFile): JvmCompilationResult =
        KotlinCompilation().apply {
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(DebuggableCompilerPluginRegistrar())
            inheritClassPath = true
            messageOutputStream = System.out
        }.compile()

    protected fun captureSystemOut(block: () -> Unit): String {
        val baos = ByteArrayOutputStream()
        val original = System.out
        System.setOut(PrintStream(baos))
        try {
            block()
        } finally {
            System.setOut(original)
        }
        return baos.toString()
    }

    protected fun JvmCompilationResult.getObject(name: String): Any =
        classLoader.loadClass(name).getDeclaredField("INSTANCE").get(null)

    protected fun JvmCompilationResult.getInstance(name: String): Any =
        classLoader.loadClass(name).getDeclaredConstructor().newInstance()

    protected fun Any.call(method: String, vararg args: Any?): Any? {
        val types = args.map { it?.javaClass ?: Any::class.java }.toTypedArray()
        return javaClass.methods.first { it.name == method }.invoke(this, *args)
    }
}
