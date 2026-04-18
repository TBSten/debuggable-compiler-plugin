package example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.tbsten.debuggable.runtime.annotations.Debuggable
import me.tbsten.debuggable.runtime.annotations.FocusDebuggable

private val GREETINGS = listOf("Hello", "Bonjour", "こんにちは", "Hola", "Hallo", "안녕하세요")

@Debuggable(isSingleton = true)
object CounterStore {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    private val _label = MutableStateFlow(GREETINGS.first())
    val label: StateFlow<String> = _label.asStateFlow()

    private var labelIndex = 0

    fun increment() {
        _count.update { it + 1 }
    }

    fun decrement() {
        _count.update { it - 1 }
    }

    fun nextLabel() {
        labelIndex = (labelIndex + 1) % GREETINGS.size
        _label.value = GREETINGS[labelIndex]
    }
}

@Composable
fun App() {
    val count by CounterStore.count.collectAsState()
    val label by CounterStore.label.collectAsState()

    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Debuggable CMP Sample", style = MaterialTheme.typography.headlineMedium)
            Text("Open the terminal — you will see [Debuggable] logs when state changes.")
            Spacer(Modifier.width(8.dp))
            Text("$label: $count", style = MaterialTheme.typography.headlineLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { CounterStore.increment() }) { Text("+1") }
                Button(onClick = { CounterStore.decrement() }) { Text("-1") }
                Button(onClick = { CounterStore.nextLabel() }) {
                    Text("Next label")
                }
            }
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Debuggable CMP Sample") {
        App()
    }
}
