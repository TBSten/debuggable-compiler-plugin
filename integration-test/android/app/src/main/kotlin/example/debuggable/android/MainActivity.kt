package example.debuggable.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.tbsten.debuggable.runtime.annotations.Debuggable
import me.tbsten.debuggable.runtime.annotations.FocusDebuggable

private val STATUSES = listOf("Idle", "Loading", "Running", "Success", "Error", "Done")

@Debuggable
class CounterViewModel : ViewModel() {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    private val _label = MutableStateFlow(STATUSES.first())
    val label: StateFlow<String> = _label.asStateFlow()

    @FocusDebuggable
    var labelIndex = 0

    fun increment() {
        _count.update { it + 1 }
    }

    fun nextLabel() {
        labelIndex = (labelIndex + 1) % STATUSES.size
        _label.value = STATUSES[labelIndex]
    }

}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SampleScreen()
            }
        }
    }
}

@Composable
fun SampleScreen(vm: CounterViewModel = viewModel()) {
    val count by vm.count.collectAsState()
    val label by vm.label.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Debuggable Android Sample", style = MaterialTheme.typography.headlineMedium)
        Text("Check Logcat for [Debuggable] logs when state changes.")
        Text("$label: $count", style = MaterialTheme.typography.headlineLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::increment) { Text("+1") }
            Button(onClick = vm::nextLabel) { Text("Next label") }
        }
    }
}
