package com.example.uir_android.ui.screen

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.uir_android.BuildConfig
import com.example.uir_android.domain.model.RegistrationGroup
import com.example.uir_android.ui.component.AppTopBar
import com.example.uir_android.ui.event.handle
import com.example.uir_android.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onOpenRegister: () -> Unit,
    onOpenOfflineEmulator: () -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val authState by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            event.handle(snackbarHostState)
        }
    }

    LaunchedEffect(authState.isLoggedIn) {
        if (authState.isLoggedIn) password = ""
    }

    Scaffold(
        topBar = { AppTopBar(title = "Вход") },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                enabled = !authState.isSubmitting
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        viewModel.login(email, password)
                    }
                ),
                enabled = !authState.isSubmitting
            )
            Button(
                onClick = { viewModel.login(email, password) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !authState.isSubmitting
            ) {
                if (authState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Войти")
                }
            }
            OutlinedButton(
                onClick = {
                    password = ""
                    onOpenRegister()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !authState.isSubmitting
            ) {
                Text("Регистрация")
            }
            OutlinedButton(
                onClick = {
                    password = ""
                    onOpenOfflineEmulator()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !authState.isSubmitting
            ) {
                Text("Открыть эмулятор без входа")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    viewModel: AuthViewModel,
    onOpenLogin: () -> Unit
) {
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirmation by remember { mutableStateOf("") }
    var selectedGroupId by rememberSaveable { mutableStateOf<Int?>(null) }
    val authState by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(viewModel) {
        viewModel.loadRegistrationGroups()
        viewModel.events.collectLatest { event ->
            event.handle(snackbarHostState)
        }
    }

    LaunchedEffect(authState.isLoggedIn) {
        if (authState.isLoggedIn) {
            password = ""
            passwordConfirmation = ""
        }
    }

    val leaveRegistration = {
        password = ""
        passwordConfirmation = ""
        onOpenLogin()
    }

    LaunchedEffect(authState.registrationGroups, selectedGroupId) {
        if (selectedGroupId == null && authState.registrationGroups.size == 1) {
            selectedGroupId = authState.registrationGroups.first().id
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "Регистрация", onBack = leaveRegistration) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Имя") },
                enabled = !authState.isSubmitting
            )
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Фамилия") },
                enabled = !authState.isSubmitting
            )
            GroupSelector(
                groups = authState.registrationGroups,
                selectedGroupId = selectedGroupId,
                isLoading = authState.isGroupsLoading,
                enabled = !authState.isSubmitting,
                onSelect = { selectedGroupId = it.id }
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                enabled = !authState.isSubmitting
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                enabled = !authState.isSubmitting
            )
            OutlinedTextField(
                value = passwordConfirmation,
                onValueChange = { passwordConfirmation = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Повторите пароль") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                enabled = !authState.isSubmitting
            )

            Text(
                text = if (authState.captchaVerified) {
                    "CAPTCHA пройдена"
                } else {
                    "Для регистрации подтвердите, что вы не робот"
                },
                color = if (authState.captchaVerified) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            OutlinedButton(
                onClick = {
                    viewModel.clearCaptcha()
                    openCaptcha(context)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !authState.isSubmitting
            ) {
                Text(
                    if (authState.captchaVerified) {
                        "Пройти CAPTCHA заново"
                    } else {
                        "Пройти CAPTCHA"
                    }
                )
            }
            Button(
                onClick = {
                    viewModel.register(
                        firstName = firstName,
                        lastName = lastName,
                        groupText = selectedGroupId?.toString().orEmpty(),
                        email = email,
                        password = password,
                        passwordConfirmation = passwordConfirmation
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !authState.isSubmitting &&
                    authState.captchaVerified &&
                    selectedGroupId != null
            ) {
                if (authState.isSubmitting) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Text("Регистрация...")
                    }
                } else {
                    Text("Зарегистрироваться")
                }
            }
            OutlinedButton(
                onClick = leaveRegistration,
                modifier = Modifier.fillMaxWidth(),
                enabled = !authState.isSubmitting
            ) {
                Text("Назад ко входу")
            }
        }
    }
}

@Composable
private fun GroupSelector(
    groups: List<RegistrationGroup>,
    selectedGroupId: Int?,
    isLoading: Boolean,
    enabled: Boolean,
    onSelect: (RegistrationGroup) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = groups.firstOrNull { it.id == selectedGroupId }?.name

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && groups.isNotEmpty()
        ) {
            Text(
                when {
                    isLoading -> "Загрузка групп..."
                    selectedName != null -> selectedName
                    groups.isEmpty() -> "Группы недоступны"
                    else -> "Выберите группу"
                }
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            groups.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.name) },
                    onClick = {
                        onSelect(group)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun openCaptcha(context: Context) {
    val url = BuildConfig.SERVER_BASE_URL.trimEnd('/') + "/api/mobile/captcha"
    val uri = Uri.parse(url)
    runCatching {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    }.onFailure {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
