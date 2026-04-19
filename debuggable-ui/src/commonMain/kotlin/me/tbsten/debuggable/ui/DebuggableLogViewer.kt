package me.tbsten.debuggable.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Scrollable in-app log viewer that renders every message captured by [logger].
 *
 * Features:
 * - Live: new entries appear at the bottom and auto-scroll into view
 * - Search: an always-visible text field filters entries by substring (case-insensitive)
 * - Monospace: messages render in [FontFamily.Monospace] so padded numeric values line up
 *
 * Designed to be embedded anywhere in a Compose tree — typically a debug drawer
 * or a dedicated screen hidden behind a long-press / shake gesture:
 *
 * ```
 * val logger = remember { UiDebugLogger(bufferSize = 500) }
 * DisposableEffect(logger) {
 *     val prev = DefaultDebugLogger.current
 *     DefaultDebugLogger.current = logger
 *     onDispose { DefaultDebugLogger.current = prev }
 * }
 * DebuggableLogViewer(logger, modifier = Modifier.fillMaxSize())
 * ```
 *
 * @param logger Source of captured entries. Typically the same [UiDebugLogger]
 *   that was installed as [me.tbsten.debuggable.runtime.logging.DefaultDebugLogger.current].
 * @param modifier Forwarded to the root [Column].
 */
@Composable
fun DebuggableLogViewer(
    logger: UiDebugLogger,
    modifier: Modifier = Modifier,
) {
    val entries by logger.entries.collectAsState()
    var query by remember { mutableStateOf("") }

    val filtered = remember(entries, query) {
        if (query.isBlank()) entries
        else entries.filter { it.message.contains(query, ignoreCase = true) }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty()) listState.animateScrollToItem(filtered.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Filter") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(filtered, key = { it.sequence }) { entry ->
                Text(
                    text = entry.message,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}
