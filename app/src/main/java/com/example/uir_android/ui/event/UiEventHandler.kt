package com.example.uir_android.ui.event

import androidx.compose.material3.SnackbarHostState

suspend fun UiEvent.handle(
    snackbarHostState: SnackbarHostState,
    onNavigate: (String) -> Unit = {}
) {
    when (this) {
        is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(message)
        is UiEvent.Navigate -> onNavigate(route)
    }
}
