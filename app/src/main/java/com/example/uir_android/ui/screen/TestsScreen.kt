package com.example.uir_android.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.uir_android.domain.model.TestResult
import com.example.uir_android.domain.model.TestSummary
import com.example.uir_android.ui.component.AppTopBar
import com.example.uir_android.ui.viewmodel.TestsViewModel
import com.example.uir_android.ui.event.handle
import com.example.uir_android.ui.viewmodel.isCompleted
import kotlinx.coroutines.flow.collectLatest

@Composable
fun TestsScreen(
    onBack: () -> Unit,
    onOpenTest: (Int) -> Unit,
    onOpenResult: (Int) -> Unit,
    onOpenEmulatorControl: (Int) -> Unit,
    onOpenEmulatorResult: (Int, Int) -> Unit,
    viewModel: TestsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { it.handle(snackbarHostState) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.refresh()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Тесты",
                onBack = onBack,
                actions = {
                    OutlinedButton(
                        onClick = viewModel::refresh,
                        enabled = !state.isLoading,
                        modifier = Modifier.widthIn(min = 112.dp)
                    ) {
                        Text(if (state.isLoading) "Загрузка..." else "Обновить")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.tests.isEmpty() && state.emulatorControls.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = state.errorMessage ?: "\u0414\u043e\u0441\u0442\u0443\u043f\u043d\u044b\u0445 \u0442\u0435\u0441\u0442\u043e\u0432 \u043d\u0435\u0442",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(
                            onClick = viewModel::refresh,
                            modifier = Modifier.widthIn(min = 160.dp)
                        ) {
                            Text("\u041e\u0431\u043d\u043e\u0432\u0438\u0442\u044c")
                        }
                    }
                }
                else -> {
                    val completedTests = state.tests.filter(TestSummary::isCompleted)
                    val activeTests = state.tests.filter { test ->
                        !test.isCompleted() &&
                            (test.hasCurrentRun || test.id in state.localDraftTestIds)
                    }
                    val otherTests = state.tests.filterNot { test ->
                        test.isCompleted() || test.hasCurrentRun || test.id in state.localDraftTestIds
                    }
                    val completedControls = state.emulatorControls.filter(TestSummary::isCompleted)
                    val activeControls = state.emulatorControls.filter { control ->
                        !control.isCompleted() &&
                            (control.hasCurrentRun || control.id in state.localDraftControlIds)
                    }
                    val otherControls = state.emulatorControls.filterNot { control ->
                        control.isCompleted() || control.hasCurrentRun ||
                            control.id in state.localDraftControlIds
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (activeTests.isNotEmpty() || activeControls.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Начатые",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(activeTests, key = { "active-${it.id}" }) { test ->
                                ActiveTestCard(test = test, onOpenTest = onOpenTest)
                            }
                            items(activeControls, key = { "active-control-${it.id}" }) { control ->
                                EmulatorControlCard(
                                    control = control,
                                    onOpenControl = onOpenEmulatorControl,
                                    onOpenResult = onOpenEmulatorResult
                                )
                            }
                        }

                        if (completedTests.isNotEmpty() || completedControls.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Пройденные",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(completedTests, key = { "completed-${it.id}" }) { test ->
                                CompletedTestCard(
                                    test = test,
                                    result = state.completedResults[test.id],
                                    onOpenResult = onOpenResult
                                )
                            }
                            items(
                                completedControls,
                                key = { "completed-control-${it.id}" }
                            ) { control ->
                                EmulatorControlCard(
                                    control = control,
                                    onOpenControl = onOpenEmulatorControl,
                                    onOpenResult = onOpenEmulatorResult
                                )
                            }
                        }

                        if (otherTests.isNotEmpty() || otherControls.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Доступные",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        items(otherTests, key = { it.id }) { test ->
                            TestCard(test = test, onOpenTest = onOpenTest)
                        }
                        items(otherControls, key = { "control-${it.id}" }) { control ->
                            EmulatorControlCard(
                                control = control,
                                onOpenControl = onOpenEmulatorControl,
                                onOpenResult = onOpenEmulatorResult
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedTestCard(
    test: TestSummary,
    result: TestResult?,
    onOpenResult: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(test.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Тест")
            Text("Тест пройден")
            result?.let {
                Text("Баллы: ${it.score}")
                Text("Оценка: ${it.markRu.ifBlank { it.markEu }}")
            }
            Button(
                onClick = { onOpenResult(test.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Посмотреть результат")
            }
        }
    }
}

@Composable
private fun ActiveTestCard(
    test: TestSummary,
    onOpenTest: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = test.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "\u041f\u043e\u043f\u044b\u0442\u043a\u0430 \u0443\u0436\u0435 \u043d\u0430\u0447\u0430\u0442\u0430. \u0412\u0440\u0435\u043c\u044f \u043f\u0440\u043e\u0434\u043e\u043b\u0436\u0430\u0435\u0442 \u0438\u0434\u0442\u0438.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = { onOpenTest(test.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("\u0412\u0435\u0440\u043d\u0443\u0442\u044c\u0441\u044f \u043a \u0442\u0435\u0441\u0442\u0443")
            }
        }
    }
}

@Composable
private fun TestCard(
    test: TestSummary,
    onOpenTest: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = test.name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            if (test.course.isNotBlank()) {
                Text(text = test.course, style = MaterialTheme.typography.bodyMedium)
            }
            Text(text = "\u0422\u0438\u043f: ${test.type.ifBlank { "\u043d\u0435 \u0443\u043a\u0430\u0437\u0430\u043d" }}")
            Text(text = "\u0412\u043e\u043f\u0440\u043e\u0441\u043e\u0432: ${test.questionsCount}, \u043f\u043e\u043f\u044b\u0442\u043e\u043a: ${test.attempts}")
            Text(text = "\u0412\u0440\u0435\u043c\u044f: ${test.timeMinutes} \u043c\u0438\u043d., \u043c\u0430\u043a\u0441\u0438\u043c\u0443\u043c: ${test.maxPoints.formatPoints()}")
            Button(
                onClick = { onOpenTest(test.id) },
                enabled = test.available,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (test.hasCurrentRun) "\u041f\u0440\u043e\u0434\u043e\u043b\u0436\u0438\u0442\u044c" else "\u041d\u0430\u0447\u0430\u0442\u044c")
            }
        }
    }
}

private fun Double.formatPoints(): String {
    val longValue = toLong()
    return if (this == longValue.toDouble()) {
        longValue.toString()
    } else {
        String.format("%.1f", this)
    }
}
