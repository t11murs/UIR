package com.example.uir_android.ui.event

sealed interface UiEvent {
    data class ShowSnackbar(val message: String) : UiEvent

    data class Navigate(val route: String) : UiEvent
}
