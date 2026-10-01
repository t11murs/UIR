package com.example.uir_android.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.uir_android.ui.util.formatSymbolForUi
import com.example.uir_android.core.util.formatTimestamp
import com.example.uir_android.domain.model.TapeCell
import com.example.uir_android.domain.model.TmProgram
import com.example.uir_android.domain.model.TmRun
import com.example.uir_android.domain.model.TmTraceEntry

@Composable
fun MenuCard(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun TapeWindow(cells: List<TapeCell>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        items(cells, key = { it.index }) { cell ->
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .size(width = 42.dp, height = 56.dp)
                    .border(width = 1.dp, color = colors.outline)
                    .background(
                        if (cell.isHead) colors.tertiaryContainer else colors.surface
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = formatSymbolForUi(cell.symbol),
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (cell.isHead) {
                        colors.onTertiaryContainer
                    } else {
                        colors.onSurface
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun TraceLog(trace: List<TmTraceEntry>, modifier: Modifier = Modifier) {
    if (trace.isEmpty()) return

    LazyColumn(
        modifier = modifier.height(240.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(trace, key = { it.stepNumber }) { entry ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("${entry.stepNumber}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${entry.state}, ${entry.headPos}, ${formatSymbolForUi(entry.readSymbol)} -> ${formatSymbolForUi(entry.writeSymbol)}, ${entry.move}, ${entry.nextState}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun SavedProgramsDialog(
    programs: List<TmProgram>,
    onSelect: (TmProgram) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        },
        title = {
            Text("Сохранённые алгоритмы")
        },
        text = {
            if (programs.isEmpty()) {
                Text("Сохранённых алгоритмов пока нет")
            } else {
                LazyColumn(
                    modifier = modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(programs, key = { it.id }) { program ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(program) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(program.name, style = MaterialTheme.typography.titleMedium)
                                if (program.description.isNotBlank()) {
                                    Text(
                                        text = program.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun RunsHistory(runs: List<TmRun>, modifier: Modifier = Modifier) {
    if (runs.isEmpty()) return

    LazyColumn(
        modifier = modifier.height(220.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(runs, key = { it.id }) { run ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("#${run.id}", style = MaterialTheme.typography.titleSmall)
                    Text(formatTimestamp(run.createdAt))
                    Text("${run.stepsCount}, ${run.endedState}")
                    if (!run.errorMessage.isNullOrBlank()) {
                        Text(
                            text = run.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
