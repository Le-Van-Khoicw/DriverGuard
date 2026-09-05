package com.example.driverguard.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.driverguard.feature.auth.data.FirebaseAuthRepository
import com.example.driverguard.feature.auth.data.GoogleAuthClient
import com.example.driverguard.feature.auth.monitoring.MonitoringScreen
import com.example.driverguard.feature.auth.monitoring.MonitoringViewModel
import com.example.driverguard.feature.auth.presentation.AuthViewModel
import com.example.driverguard.feature.auth.presentation.AuthViewModelFactory
import com.example.driverguard.feature.auth.presentation.ForgotPasswordScreen
import com.example.driverguard.feature.auth.presentation.LoginScreen
import com.example.driverguard.feature.auth.presentation.RegisterScreen
import com.example.driverguard.feature.auth.presentation.VerifyEmailScreen
import com.example.driverguard.feature.history.AlertDetailScreen
import com.example.driverguard.feature.history.HistoryScreen
import com.example.driverguard.feature.home.persentation.HomeScreen
import com.example.driverguard.feature.settings.DevicesScreen
import com.example.driverguard.feature.settings.ProfileScreen
import com.example.driverguard.feature.settings.SettingsScreen
import com.example.driverguard.feature.settings.VehiclesScreen
import com.example.driverguard.feature.splash.SplashScreen
import kotlinx.coroutines.launch

private object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"; const val REGISTER = "register"; const val VERIFY = "verify"; const val FORGOT = "forgot"
    const val HOME = "home"; const val MONITORING = "monitoring"; const val HISTORY = "history"
    const val ALERT = "alert/{alertId}"; const val SETTINGS = "settings"
    const val PROFILE = "profile"; const val DEVICES = "devices"; const val VEHICLES = "vehicles"
}

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { FirebaseAuthRepository() }
    val google = remember(context) { GoogleAuthClient(context) }
    val auth: AuthViewModel = viewModel(factory = AuthViewModelFactory(repository))
    val authState by auth.uiState.collectAsState()

    fun go(route: String, clear: Boolean = false) {
        nav.navigate(route) {
            if (clear) popUpTo(nav.graph.startDestinationId) { inclusive = true }
            launchSingleTop = true
        }
    }
    fun select(tab: MainTab) = go(when (tab) {
        MainTab.HOME -> Routes.HOME; MainTab.MONITORING -> Routes.MONITORING
        MainTab.HISTORY -> Routes.HISTORY; MainTab.SETTINGS -> Routes.SETTINGS
    })

    NavHost(navController = nav, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen {
                val nextRoute = when {
                    authState.canEnterApp -> Routes.HOME
                    authState.isAuthenticated -> Routes.VERIFY
                    else -> Routes.LOGIN
                }
                go(nextRoute, clear = true)
            }
        }
        composable(Routes.LOGIN) {
            val activity = context as? android.app.Activity
            LoginScreen(auth, { go(Routes.HOME, true) }, { go(Routes.VERIFY, true) },
                { go(Routes.REGISTER) }, { go(Routes.FORGOT) }, {
                    if (activity != null) {
                        scope.launch {
                            google.signIn(activity)
                                .onSuccess(auth::onGoogleLoginSuccess)
                                .onFailure(auth::onGoogleSignInError)
                        }
                    }
                })
        }
        composable(Routes.REGISTER) {
            RegisterScreen(auth, { nav.popBackStack() }, { go(Routes.VERIFY, true) }, { go(Routes.HOME, true) })
        }
        composable(Routes.FORGOT) { ForgotPasswordScreen(auth) { nav.popBackStack() } }
        composable(Routes.VERIFY) {
            VerifyEmailScreen(auth, { go(Routes.HOME, true) }) { auth.logout(); go(Routes.LOGIN, true) }
        }
        composable(Routes.HOME) {
            MainScaffold(MainTab.HOME, ::select) { HomeScreen({ go(Routes.MONITORING) }, { go(Routes.HISTORY) }) }
        }
        composable(Routes.MONITORING) {
            val vm: MonitoringViewModel = viewModel()
            MainScaffold(MainTab.MONITORING, ::select) { MonitoringScreen(vm) }
        }
        composable(Routes.HISTORY) {
            MainScaffold(MainTab.HISTORY, ::select) { HistoryScreen { go("alert/$it") } }
        }
        composable(Routes.ALERT, arguments = listOf(navArgument("alertId") { type = NavType.StringType })) {
            AlertDetailScreen(it.arguments?.getString("alertId").orEmpty()) { nav.popBackStack() }
        }
        composable(Routes.SETTINGS) {
            MainScaffold(MainTab.SETTINGS, ::select) {
                SettingsScreen({ go(Routes.PROFILE) }, { go(Routes.DEVICES) }, { go(Routes.VEHICLES) }) {
                    auth.logout(); go(Routes.LOGIN, true)
                }
            }
        }
        composable(Routes.PROFILE) { ProfileScreen(user = authState.currentUser) { nav.popBackStack() } }
        composable(Routes.DEVICES) { DevicesScreen { nav.popBackStack() } }
        composable(Routes.VEHICLES) { VehiclesScreen { nav.popBackStack() } }
    }
}
