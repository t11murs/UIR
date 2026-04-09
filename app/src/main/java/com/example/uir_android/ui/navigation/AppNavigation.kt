package com.example.uir_android.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.uir_android.ui.screen.HomeScreen
import com.example.uir_android.ui.screen.LoginScreen
import com.example.uir_android.ui.screen.MenuScreen
import com.example.uir_android.ui.screen.ProfileScreen
import com.example.uir_android.ui.screen.RegisterScreen
import com.example.uir_android.ui.screen.TuringScreen
import com.example.uir_android.ui.viewmodel.AuthViewModel

sealed class AppDestination(val route: String) {
    data object Login : AppDestination("login")
    data object Register : AppDestination("register")
    data object Home : AppDestination("home")
    data object Menu : AppDestination("menu")
    data object Turing : AppDestination("turing")
    data object Profile : AppDestination("profile")
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit
)

private val authRoutes = setOf(
    AppDestination.Login.route,
    AppDestination.Register.route
)

private val bottomBarRoutes = setOf(
    AppDestination.Menu.route,
    AppDestination.Home.route,
    AppDestination.Profile.route,
    AppDestination.Turing.route
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
fun AppNavHost() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    if (!authState.isReady) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination = if (authState.isLoggedIn) {
        AppDestination.Home.route
    } else {
        AppDestination.Login.route
    }

    LaunchedEffect(authState.isLoggedIn, currentRoute) {
        val route = currentRoute ?: return@LaunchedEffect
        when {
            authState.isLoggedIn && route in authRoutes -> {
                navController.navigate(AppDestination.Home.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }

            !authState.isLoggedIn && route !in authRoutes -> {
                navController.navigate(AppDestination.Login.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = true
                    }
                    launchSingleTop = true
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            val destination = backStackEntry?.destination
            if (authState.isLoggedIn && destination.shouldShowBottomBar()) {
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
                    onOpenRegister = { navController.navigate(AppDestination.Register.route) }
                )
            }

            composable(AppDestination.Register.route) {
                RegisterScreen(
                    viewModel = authViewModel,
                    onOpenLogin = { navController.navigate(AppDestination.Login.route) }
                )
            }

            composable(AppDestination.Home.route) {
                HomeScreen(email = authState.email)
            }

            composable(AppDestination.Menu.route) {
                MenuScreen(
                    onOpenEmulator = { navController.navigate(AppDestination.Turing.route) }
                )
            }

            composable(AppDestination.Turing.route) {
                TuringScreen()
            }

            composable(AppDestination.Profile.route) {
                ProfileScreen(
                    onLogout = authViewModel::logout,
                    email = authState.email,
                    isSubmitting = authState.isSubmitting
                )
            }
        }
    }
}

private fun NavDestination?.shouldShowBottomBar(): Boolean {
    return this?.route in bottomBarRoutes
}

private fun NavDestination?.isRouteSelected(route: String): Boolean {
    if (this == null) return false
    return hierarchy.any { destination -> destination.route == route }
}
