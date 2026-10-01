package com.example.uir_android.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.uir_android.ui.screen.EmulatorControlScreen
import com.example.uir_android.ui.screen.EmulatorControlsScreen
import com.example.uir_android.ui.screen.HomeScreen
import com.example.uir_android.ui.screen.LoginScreen
import com.example.uir_android.ui.screen.LectureAttendanceScreen
import com.example.uir_android.ui.screen.MenuScreen
import com.example.uir_android.ui.screen.NativeTestScreen
import com.example.uir_android.ui.screen.NewsDetailScreen
import com.example.uir_android.ui.screen.ProfileScreen
import com.example.uir_android.ui.screen.RegisterScreen
import com.example.uir_android.ui.screen.SeminarAttendanceScreen
import com.example.uir_android.ui.screen.TestsScreen
import com.example.uir_android.ui.screen.TuringScreen
import com.example.uir_android.ui.viewmodel.AuthViewModel
import com.example.uir_android.ui.viewmodel.TestsViewModel
import com.example.uir_android.domain.model.AppThemeMode
import com.example.uir_android.domain.model.LocalStorageStatus
import com.example.uir_android.ui.state.TestsUiState
import com.example.uir_android.ui.state.ActiveAssessmentType

sealed class AppDestination(val route: String) {
    data object Login : AppDestination("login")
    data object Register : AppDestination("register")
    data object Home : AppDestination("home")
    data object Menu : AppDestination("menu")
    data object Turing : AppDestination("turing")
    data object Tests : AppDestination("tests")
    data object TestDetail : AppDestination("test_detail/{testId}?review={review}") {
        fun createRoute(testId: Int, review: Boolean = false): String =
            "test_detail/$testId?review=$review"
    }
    data object EmulatorControls : AppDestination("emulator_controls")
    data object EmulatorControlDetail : AppDestination(
        "emulator_control/{controlId}?review={review}&runId={runId}"
    ) {
        fun createRoute(
            controlId: Int,
            review: Boolean = false,
            runId: Int = 0
        ): String = "emulator_control/$controlId?review=$review&runId=$runId"
    }
    data object Profile : AppDestination("profile")
    data object LectureAttendance : AppDestination("lecture_attendance")
    data object SeminarAttendance : AppDestination("seminar_attendance")
    data object NewsDetail : AppDestination("news/{newsId}") {
        fun createRoute(newsId: String): String = "news/${Uri.encode(newsId)}"
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit
)

private enum class ActiveAssessmentKind {
    TEST,
    EMULATOR_CONTROL
}

private data class ActiveAssessment(
    val kind: ActiveAssessmentKind,
    val id: Int,
    val runId: Int
) {
    val route: String
        get() = when (kind) {
            ActiveAssessmentKind.TEST -> AppDestination.TestDetail.createRoute(id)
            ActiveAssessmentKind.EMULATOR_CONTROL ->
                AppDestination.EmulatorControlDetail.createRoute(id)
        }
}

private val authRoutes = setOf(
    AppDestination.Login.route,
    AppDestination.Register.route
)

private val publicRoutes = authRoutes + AppDestination.Turing.route

private val bottomBarRoutes = setOf(
    AppDestination.Menu.route,
    AppDestination.Home.route,
    AppDestination.Tests.route,
    AppDestination.EmulatorControls.route,
    AppDestination.Profile.route,
    AppDestination.Turing.route,
    AppDestination.LectureAttendance.route,
    AppDestination.SeminarAttendance.route,
    AppDestination.NewsDetail.route
)

private val bottomNavItems = listOf(
    BottomNavItem(
        route = AppDestination.Menu.route,
        label = "Меню",
        icon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) }
    ),
    BottomNavItem(
        route = AppDestination.Home.route,
        label = "Главная",
        icon = { Icon(Icons.Outlined.Home, contentDescription = null) }
    ),
    BottomNavItem(
        route = AppDestination.Profile.route,
        label = "Профиль",
        icon = { Icon(Icons.Outlined.Person, contentDescription = null) }
    )
)

@Composable
fun AppNavHost(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val testsViewModel: TestsViewModel = hiltViewModel()
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val testsState by testsViewModel.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0

    LaunchedEffect(authState.isLoggedIn, authState.email) {
        testsViewModel.onSessionChanged(authState.isLoggedIn, authState.email)
    }

    if (!authState.isReady) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (authState.storageStatus != LocalStorageStatus.FAILED) {
                    CircularProgressIndicator()
                }
                authState.storageMessage?.let { message ->
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }
        return
    }

    if (shouldBlockAssessmentNavigation(authState.isLoggedIn, testsState)) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (testsState.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = testsState.errorMessage ?: "Не удалось проверить активные попытки",
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Button(onClick = testsViewModel::refresh) {
                        Text("Повторить")
                    }
                }
            }
        }
        return
    }

    val activeAssessment = testsState.activeAssessment()

    val storageSnackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(authState.storageMessage) {
        authState.storageMessage?.let { storageSnackbarHostState.showSnackbar(it) }
    }

    val startDestination = if (authState.isLoggedIn) {
        activeAssessment?.route ?: AppDestination.Home.route
    } else {
        AppDestination.Login.route
    }

    LaunchedEffect(
        authState.isLoggedIn,
        authState.captchaVerified,
        currentRoute,
        activeAssessment
    ) {
        val route = currentRoute ?: return@LaunchedEffect
        when {
            authState.isLoggedIn && route in authRoutes -> {
                navController.navigate(activeAssessment?.route ?: AppDestination.Home.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }

            authState.captchaVerified && route == AppDestination.Login.route -> {
                navController.navigate(AppDestination.Register.route) {
                    launchSingleTop = true
                }
            }

            !authState.isLoggedIn && route !in publicRoutes -> {
                navController.navigate(AppDestination.Login.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
        }
    }


    LaunchedEffect(activeAssessment, currentRoute, backStackEntry?.arguments) {
        val active = activeAssessment ?: return@LaunchedEffect
        val route = currentRoute ?: return@LaunchedEffect
        if (!authState.isLoggedIn || route in authRoutes) return@LaunchedEffect
        if (!backStackEntry.isShowing(active)) {
            navController.navigate(active.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(storageSnackbarHostState) },
        bottomBar = {
            val destination = backStackEntry?.destination
            if (
                authState.isLoggedIn &&
                !isKeyboardVisible &&
                destination.shouldShowBottomBar()
            ) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = destination.isRouteSelected(item.route)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = item.icon,
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(AppDestination.Login.route) {
                LoginScreen(
                    viewModel = authViewModel,
                    onOpenRegister = { navController.navigate(AppDestination.Register.route) },
                    onOpenOfflineEmulator = {
                        navController.navigate(AppDestination.Turing.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(AppDestination.Register.route) {
                RegisterScreen(
                    viewModel = authViewModel,
                    onOpenLogin = { navController.navigate(AppDestination.Login.route) }
                )
            }

            composable(AppDestination.Home.route) {
                HomeScreen(
                    email = authState.email,
                    onOpenNews = { newsId ->
                        navController.navigate(AppDestination.NewsDetail.createRoute(newsId))
                    }
                )
            }

            composable(AppDestination.Menu.route) {
                MenuScreen(
                    onOpenEmulator = { navController.navigate(AppDestination.Turing.route) },
                    onOpenTests = { navController.navigate(AppDestination.Tests.route) },
                    onOpenLectureAttendance = {
                        navController.navigate(AppDestination.LectureAttendance.route)
                    },
                    onOpenSeminarAttendance = {
                        navController.navigate(AppDestination.SeminarAttendance.route)
                    }
                )
            }

            composable(AppDestination.Turing.route) {
                TuringScreen(onBack = { navController.popBackStack() })
            }

            composable(AppDestination.Tests.route) {
                TestsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTest = { testId ->
                        navController.navigate(AppDestination.TestDetail.createRoute(testId, review = false))
                    },
                    onOpenResult = { testId ->
                        navController.navigate(AppDestination.TestDetail.createRoute(testId, review = true))
                    },
                    onOpenEmulatorControl = { controlId ->
                        navController.navigate(
                            AppDestination.EmulatorControlDetail.createRoute(controlId)
                        )
                    },
                    onOpenEmulatorResult = { controlId, runId ->
                        navController.navigate(
                            AppDestination.EmulatorControlDetail.createRoute(
                                controlId = controlId,
                                review = true,
                                runId = runId
                            )
                        )
                    },
                    viewModel = testsViewModel
                )
            }

            composable(
                route = AppDestination.TestDetail.route,
                arguments = listOf(
                    navArgument("testId") { type = NavType.IntType },
                    navArgument("review") {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { entry ->
                val review = entry.arguments?.getBoolean("review") ?: false
                NativeTestScreen(
                    onBack = { navController.popBackStack() },
                    lockNavigationWhileLoading = !review,
                    onStarted = { testId, runId ->
                        if (!review) {
                            testsViewModel.markAssessmentStarted(
                                assessmentId = testId,
                                runId = runId,
                                emulatorControl = false
                            )
                        }
                    },
                    onFinished = { testId ->
                        if (!review) {
                            testsViewModel.markAssessmentFinished(testId, emulatorControl = false)
                        }
                    }
                )
            }

            composable(AppDestination.EmulatorControls.route) {
                EmulatorControlsScreen(
                    onOpenControl = { controlId ->
                        navController.navigate(
                            AppDestination.EmulatorControlDetail.createRoute(controlId)
                        )
                    },
                    onOpenResult = { controlId, runId ->
                        navController.navigate(
                            AppDestination.EmulatorControlDetail.createRoute(
                                controlId = controlId,
                                review = true,
                                runId = runId
                            )
                        )
                    }
                )
            }

            composable(
                route = AppDestination.EmulatorControlDetail.route,
                arguments = listOf(
                    navArgument("controlId") { type = NavType.IntType },
                    navArgument("review") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                    navArgument("runId") {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                )
            ) { entry ->
                val review = entry.arguments?.getBoolean("review") ?: false
                EmulatorControlScreen(
                    onBack = { navController.popBackStack() },
                    reviewMode = review,
                    onStarted = { controlId, runId ->
                        if (!review) {
                            testsViewModel.markAssessmentStarted(
                                assessmentId = controlId,
                                runId = runId,
                                emulatorControl = true
                            )
                        }
                    },
                    onFinished = { controlId ->
                        if (!review) {
                            testsViewModel.markAssessmentFinished(controlId, emulatorControl = true)
                        }
                    }
                )
            }

            composable(AppDestination.Profile.route) {
                ProfileScreen(
                    onLogout = authViewModel::logout,
                    isSubmitting = authState.isSubmitting,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange
                )
            }

            composable(AppDestination.LectureAttendance.route) {
                LectureAttendanceScreen(onBack = { navController.popBackStack() })
            }

            composable(AppDestination.SeminarAttendance.route) {
                SeminarAttendanceScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = AppDestination.NewsDetail.route,
                arguments = listOf(navArgument("newsId") { type = NavType.StringType })
            ) {
                NewsDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun TestsUiState.activeAssessment(): ActiveAssessment? {
    activeAssessmentLock?.let { lock ->
        return ActiveAssessment(
            kind = when (lock.type) {
                ActiveAssessmentType.TEST -> ActiveAssessmentKind.TEST
                ActiveAssessmentType.EMULATOR_CONTROL -> ActiveAssessmentKind.EMULATOR_CONTROL
            },
            id = lock.assessmentId,
            runId = lock.runId
        )
    }
    val activeTests = tests.asSequence()
        .filter { it.hasCurrentRun }
        .map {
            ActiveAssessment(
                kind = ActiveAssessmentKind.TEST,
                id = it.id,
                runId = it.currentResultId ?: -1
            )
        }
    val activeControls = emulatorControls.asSequence()
        .filter { it.hasCurrentRun }
        .map {
            ActiveAssessment(
                kind = ActiveAssessmentKind.EMULATOR_CONTROL,
                id = it.id,
                runId = it.currentResultId ?: -1
            )
        }
    val serverAssessment = (activeTests + activeControls).maxByOrNull(ActiveAssessment::runId)
    if (serverAssessment != null) return serverAssessment

    // A local draft protects an in-progress attempt while the server is unavailable.
    // A successful server response takes precedence and prevents stale drafts from locking navigation.
    if (!controlsRequestSucceeded) {
        localDraftControlIds.maxOrNull()?.let { controlId ->
            return ActiveAssessment(
                kind = ActiveAssessmentKind.EMULATOR_CONTROL,
                id = controlId,
                runId = -1
            )
        }
    }
    if (!testsRequestSucceeded) {
        localDraftTestIds.maxOrNull()?.let { testId ->
            return ActiveAssessment(
                kind = ActiveAssessmentKind.TEST,
                id = testId,
                runId = -1
            )
        }
    }
    return null
}

internal fun TestsUiState.activeAssessmentRoute(): String? = activeAssessment()?.route

internal fun shouldBlockAssessmentNavigation(
    isLoggedIn: Boolean,
    testsState: TestsUiState
): Boolean = isLoggedIn && !testsState.assessmentGuardReady

private fun androidx.navigation.NavBackStackEntry?.isShowing(
    assessment: ActiveAssessment
): Boolean {
    if (this == null) return false
    return when (assessment.kind) {
        ActiveAssessmentKind.TEST -> destination.route == AppDestination.TestDetail.route &&
            arguments?.getInt("testId") == assessment.id &&
            arguments?.getBoolean("review") != true
        ActiveAssessmentKind.EMULATOR_CONTROL ->
            destination.route == AppDestination.EmulatorControlDetail.route &&
                arguments?.getInt("controlId") == assessment.id &&
                arguments?.getBoolean("review") != true
    }
}

private fun NavDestination?.shouldShowBottomBar(): Boolean {
    return this?.route in bottomBarRoutes
}

private fun NavDestination?.isRouteSelected(route: String): Boolean {
    if (this == null) return false
    return hierarchy.any { destination -> destination.route == route }
}
