package com.example.driverguard.navigation

import androidx.compose.runtime.LaunchedEffect
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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.driverguard.feature.auth.data.FirebaseAuthRepository
import com.example.driverguard.feature.auth.data.GoogleAuthClient
import com.example.driverguard.feature.auth.monitoring.MonitoringScreen
import com.example.driverguard.feature.auth.monitoring.MonitoringViewModel
import com.example.driverguard.feature.auth.presentation.AccountLockedScreen
import com.example.driverguard.feature.auth.presentation.AuthViewModel
import com.example.driverguard.feature.auth.presentation.AuthViewModelFactory
import com.example.driverguard.feature.auth.presentation.ForgotPasswordScreen
import com.example.driverguard.feature.auth.presentation.LoginScreen
import com.example.driverguard.feature.auth.presentation.PendingApprovalScreen
import com.example.driverguard.feature.auth.presentation.RegisterScreen
import com.example.driverguard.feature.auth.presentation.VerifyEmailScreen
import com.example.driverguard.feature.history.AlertDetailScreen
import com.example.driverguard.feature.history.HistoryScreen
import com.example.driverguard.feature.home.persentation.HomeScreen
import com.example.driverguard.feature.settings.DevicesScreen
import com.example.driverguard.feature.settings.DriverDossierScreen
import com.example.driverguard.feature.settings.DriverDossierViewModel
import com.example.driverguard.feature.settings.ProfileScreen
import com.example.driverguard.feature.settings.SettingsScreen
import com.example.driverguard.feature.settings.VehiclesScreen
import com.example.driverguard.feature.splash.SplashScreen
import com.example.driverguard.feature.dispatch.DispatchViewModel
import com.example.driverguard.feature.dispatch.DispatchScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"; const val REGISTER = "register"; const val VERIFY = "verify"; const val FORGOT = "forgot"
    const val PENDING = "pending"; const val LOCKED = "locked"
    const val TRIPS = "dispatch_trips"
    const val HOME = "home"; const val MONITORING = "monitoring"; const val HISTORY = "history"
    const val ALERT = "alert/{alertId}"; const val SETTINGS = "settings"
    const val DOSSIER = "driver_dossier"
    const val PROFILE = "profile"; const val DEVICES = "devices"; const val VEHICLES = "vehicles"
}

@Composable
fun AppNavigation(notificationTripId: String? = null, onNotificationConsumed: () -> Unit = {}) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { FirebaseAuthRepository() }
    val google = remember(context) { GoogleAuthClient(context) }
    val auth: AuthViewModel = viewModel(factory = AuthViewModelFactory(repository))
    // A trip belongs to the activity, not to each new Monitoring back-stack entry.
    val monitoring: MonitoringViewModel = viewModel()
    val dispatch: DispatchViewModel = viewModel()
    val dispatchState by dispatch.state.collectAsState()
    val monitoringState by monitoring.uiState.collectAsState()
    val authState by auth.uiState.collectAsState()

    fun go(route: String, clear: Boolean = false) {
        nav.navigate(route) {
            if (clear) popUpTo(nav.graph.startDestinationId) { inclusive = true }
            launchSingleTop = true
        }
    }
    val currentEntry by nav.currentBackStackEntryAsState()
    LaunchedEffect(notificationTripId, authState.canEnterApp, currentEntry?.destination?.route) {
        val route = currentEntry?.destination?.route
        if (notificationTripId != null && authState.canEnterApp && route in listOf(Routes.HOME, Routes.TRIPS, Routes.HISTORY, Routes.SETTINGS, Routes.PROFILE, Routes.DEVICES, Routes.VEHICLES, Routes.MONITORING)) {
            go(Routes.TRIPS)
            onNotificationConsumed()
        }
    }
    fun select(tab: MainTab) = go(when (tab) {
        MainTab.TRIPS -> Routes.TRIPS
        MainTab.HOME -> Routes.HOME; MainTab.MONITORING -> Routes.MONITORING
        MainTab.HISTORY -> Routes.HISTORY; MainTab.SETTINGS -> Routes.SETTINGS
    })
    fun routeForCurrentState(): String {
        val state = auth.uiState.value
        return when {
            state.canEnterApp -> Routes.HOME
            state.isAccountLocked -> Routes.LOCKED
            state.isPendingApproval -> Routes.PENDING
            state.isAuthenticated -> Routes.VERIFY
            else -> Routes.LOGIN
        }
    }

    LaunchedEffect(dispatchState.trips, dispatchState.fromCache, dispatchState.loaded, monitoringState.dispatchTripId, monitoringState.hasActiveTrip) {
        val linkedId = monitoringState.dispatchTripId
        if (linkedId != null && monitoringState.hasActiveTrip && dispatchState.loaded && !dispatchState.fromCache) {
            val assigned = dispatchState.trips.find { it.id == linkedId }
            if (assigned == null || assigned.assignmentVersion != monitoringState.assignmentVersion || assigned.status !in listOf("in_progress", "completion_pending")) {
                monitoring.closeForDispatch(linkedId, monitoringState.assignmentVersion)
            }
        }
    }
    LaunchedEffect(authState.isAccountLocked) {
        if (authState.isAccountLocked) { monitoring.pauseMonitoring(); go(Routes.LOCKED, true) }
    }

    NavHost(navController = nav, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen {
                scope.launch {
                    // Nếu đã đăng nhập, đợi tối đa 2s để accountStatus kịp tải từ Firestore
                    if (auth.uiState.value.isAuthenticated) {
                        var waited = 0
                        while (!auth.uiState.value.isAccountStatusLoaded && waited < 2000) {
                            delay(50)
                            waited += 50
                        }
                    }
                    go(routeForCurrentState(), clear = true)
                }
            }
        }
        composable(Routes.LOGIN) {
            val activity = context as? android.app.Activity
            LoginScreen(
                viewModel = auth,
                onLoginSuccess = { go(Routes.HOME, true) },
                onPendingApproval = { go(Routes.PENDING, true) },
                onAccountLocked = { go(Routes.LOCKED, true) },
                onVerificationRequired = { go(Routes.VERIFY, true) },
                onNavigateToRegister = { go(Routes.REGISTER) },
                onNavigateToForgotPassword = { go(Routes.FORGOT) },
                onGoogleLoginClick = {
                    if (activity != null) {
                        scope.launch {
                            google.signIn(activity)
                                .onSuccess(auth::onGoogleLoginSuccess)
                                .onFailure(auth::onGoogleSignInError)
                        }
                    }
                }
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                viewModel = auth,
                onBackToLogin = { nav.popBackStack() },
                onPendingApproval = { go(Routes.PENDING, true) },
                onAccountLocked = { go(Routes.LOCKED, true) },
                onVerificationRequired = { go(Routes.VERIFY, true) },
                onRegistrationCompleted = { go(Routes.HOME, true) }
            )
        }
        composable(Routes.FORGOT) { ForgotPasswordScreen(auth) { nav.popBackStack() } }
        composable(Routes.VERIFY) {
            VerifyEmailScreen(
                viewModel = auth,
                onVerificationCompleted = { go(Routes.HOME, true) },
                onPendingApproval = { go(Routes.PENDING, true) },
                onAccountLocked = { go(Routes.LOCKED, true) },
                onUseAnotherAccount = { auth.logout(); go(Routes.LOGIN, true) }
            )
        }
        composable(Routes.PENDING) {
            LaunchedEffect(authState.canEnterApp, authState.isAccountLocked) {
                if (authState.canEnterApp) go(Routes.HOME, true)
                else if (authState.isAccountLocked) go(Routes.LOCKED, true)
            }
            PendingApprovalScreen(onOpenDossier = { go(Routes.DOSSIER) }) { auth.logout(); go(Routes.LOGIN, true) }
        }
        composable(Routes.LOCKED) {
            AccountLockedScreen(
                isRejected = authState.isAccountRejected,
                rejectReason = authState.rejectReason,
                onLogout = { auth.logout(); go(Routes.LOGIN, true) }
            )
        }
        composable(Routes.HOME) {
            MainScaffold(MainTab.HOME, ::select) {
                HomeScreen(
                    onStartMonitoring = { go(Routes.TRIPS) },
                    onOpenHistory = { go(Routes.HISTORY) },
                    onOpenVehicles = { go(Routes.VEHICLES) }
                )
            }
        }
        composable(Routes.TRIPS) {
            MainScaffold(MainTab.TRIPS, ::select) {
                DispatchScreen(dispatch, onMonitor = { trip ->
                    if (monitoring.bindDispatchTrip(trip.id, trip.assignmentVersion)) go(Routes.MONITORING)
                    else go(Routes.MONITORING)
                }, onFinishMonitoring = { trip -> monitoring.closeForDispatch(trip.id, trip.assignmentVersion) })
            }
        }
        composable(Routes.MONITORING) {
            MainScaffold(MainTab.MONITORING, ::select) { MonitoringScreen(monitoring) }
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
        composable(Routes.PROFILE) { ProfileScreen(user = authState.currentUser, onOpenDossier = { go(Routes.DOSSIER) }) { nav.popBackStack() } }
        composable(Routes.DOSSIER) {
            val dossier: DriverDossierViewModel = viewModel()
            DriverDossierScreen(dossier) { nav.popBackStack() }
        }
        composable(Routes.DEVICES) { DevicesScreen { nav.popBackStack() } }
        composable(Routes.VEHICLES) { VehiclesScreen(viewModel = dispatch) { nav.popBackStack() } }
    }
}
