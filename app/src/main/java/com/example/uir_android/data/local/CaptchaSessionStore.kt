package com.example.uir_android.data.local

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class CaptchaSessionStore @Inject constructor() {
    private val _ticket = MutableStateFlow("")
    val ticket = _ticket.asStateFlow()

    fun accept(ticket: String) {
        _ticket.value = ticket.trim()
    }

    fun clear() {
        _ticket.value = ""
    }

    fun currentTicket(): String = _ticket.value
}
