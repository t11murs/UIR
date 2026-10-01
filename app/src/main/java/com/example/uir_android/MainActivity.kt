package com.example.uir_android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uir_android.domain.model.AppThemeMode
import com.example.uir_android.domain.repository.AuthRepository
import com.example.uir_android.ui.navigation.AppNavHost
import com.example.uir_android.ui.theme.UIR_androidTheme
import com.example.uir_android.ui.viewmodel.AppThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var authRepository: AuthRepository

    private val themeViewModel: AppThemeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleCaptchaIntent(intent)
        enableEdgeToEdge()
        setContent {
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            UIR_androidTheme(darkTheme = darkTheme) {
                AppNavHost(
                    themeMode = themeMode,
                    onThemeModeChange = themeViewModel::setThemeMode
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCaptchaIntent(intent)
    }

    private fun handleCaptchaIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "uirandroid" && data.host == "registration-captcha") {
            data.getQueryParameter("ticket")
                ?.takeIf { it.isNotBlank() }
                ?.let(authRepository::acceptCaptcha)
        }
    }
}
