package com.example.uir_android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.uir_android.ui.event.UiEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

abstract class EventViewModel : ViewModel() {
    private val eventChannel = Channel<UiEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    protected fun emitSnackbar(message: String) {
        eventChannel.trySend(UiEvent.ShowSnackbar(message))
    }

    protected fun emitNavigation(route: String) {
        eventChannel.trySend(UiEvent.Navigate(route))
    }
}
