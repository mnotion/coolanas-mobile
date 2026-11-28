package com.coolanas.app

import android.content.Intent
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coolanas.app.ui.theme.CoolanasTheme
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var errorReporter: ((String) -> Unit)? = null
    private var pendingAuthError: String? = null
    private lateinit var studySyncManager: StudySyncManager
    private var activeUserId: String? = null
    private var networkSyncSignal: (() -> Unit)? = null
    private var networkCallbackRegistered = false
    private var messageNotificationNavigator: (() -> Unit)? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (validated) signalNetworkSync()
        }
    }

    @OptIn(SupabaseExperimental::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        studySyncManager = StudySyncManager(applicationContext)
        CoolanasNotifications.createChannels(applicationContext)
        handleAuthDeeplink(intent)
        setContent {
            var messageNavigationRequest by remember { mutableIntStateOf(if (intent?.getBooleanExtra(EXTRA_OPEN_MESSAGES, false) == true) 1 else 0) }
            var accountProfile by remember { mutableStateOf(CoolanasAccountProfile()) }
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { }
            )
            LaunchedEffect(Unit) {
                val prefs = getSharedPreferences("coolanas_notification_permission", MODE_PRIVATE)
                val asked = prefs.getBoolean("asked_once", false)
                if (Build.VERSION.SDK_INT >= 33 && !asked && !CoolanasNotifications.notificationsAllowed(this@MainActivity)) {
                    prefs.edit().putBoolean("asked_once", true).apply()
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            CoolanasTheme(darkMode = true) {
                var authenticatedUserId by remember { mutableStateOf<String?>(null) }
                var isStartingLogin by remember { mutableStateOf(false) }
                var errorLog by remember { mutableStateOf<String?>(null) }
                var syncTrigger by remember { mutableIntStateOf(0) }
                messageNotificationNavigator = { messageNavigationRequest++ }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    errorReporter = { message -> errorLog = message }
                    networkSyncSignal = { syncTrigger++ }
                    pendingAuthError?.let {
                        errorLog = it
                        pendingAuthError = null
                    }
                    try {
                        SupabaseClientProvider.client.auth.sessionStatus.collect { status ->
                            when (status) {
                                is SessionStatus.Authenticated -> {
                                    val user = status.session.user
                                    val userId = user?.id
                                    val metadata = user?.userMetadata
                                    fun metadataValue(vararg keys: String): String? =
                                        keys.firstNotNullOfOrNull { key ->
                                            try { metadata?.get(key)?.jsonPrimitive?.contentOrNull }
                                            catch (_: Exception) { null }
                                        }
                                    accountProfile = CoolanasAccountProfile(
                                        name = metadataValue("full_name", "name", "user_name")
                                            ?: user?.email ?: "Account",
                                        email = user?.email.orEmpty(),
                                        googleAvatarUrl = metadataValue("avatar_url", "picture")
                                    )
                                    authenticatedUserId = userId
                                    activeUserId = userId
                                    if (userId != null) StudySyncWorker.enqueue(applicationContext, userId)
                                }
                                is SessionStatus.NotAuthenticated -> {
                                    authenticatedUserId = null
                                    activeUserId = null
                                    StudyTimerNotificationService.hide(applicationContext)
                                    accountProfile = CoolanasAccountProfile()
                                }
                                else -> Unit
                            }
                        }
                    } catch (error: Exception) {
                        reportAuthError(
                            "Could not read login state: ${error.message ?: error::class.java.simpleName}",
                            error
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize().testTag("app-root"),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val userId = authenticatedUserId
                    SideEffect {
                        val fullscreenFlag = WindowManager.LayoutParams.FLAG_FULLSCREEN
                        if (userId != null) {
                            window.setFlags(fullscreenFlag, fullscreenFlag)
                        } else {
                            window.clearFlags(fullscreenFlag)
                        }
                    }
                    if (userId != null) {
                        StudyDashboardScreen(
                            userId = userId,
                            repository = studySyncManager,
                            syncVersion = syncTrigger,
                            accountProfile = accountProfile,
                            notificationNavigationTrigger = messageNavigationRequest,
                            onSignOut = {
                                scope.launch {
                                    try {
                                        SupabaseClientProvider.client.auth.signOut()
                                    } catch (error: Exception) {
                                        reportAuthError(
                                            "Could not sign out: ${error.message ?: error::class.java.simpleName}",
                                            error
                                        )
                                    }
                                }
                            }
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = "Coolanas",
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(36.dp))
                            Button(
                                onClick = {
                                    if (!isStartingLogin) {
                                        isStartingLogin = true
                                        errorLog = null
                                        scope.launch {
                                            try {
                                                SupabaseClientProvider.client.auth.signInWith(Google)
                                            } catch (error: Exception) {
                                                reportAuthError(
                                                    "Could not start Google sign-in: ${error.message ?: error::class.java.simpleName}",
                                                    error,
                                                )
                                            } finally {
                                                isStartingLogin = false
                                            }
                                        }
                                    }
                                },
                                enabled = !isStartingLogin,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(if (isStartingLogin) "Opening Google…" else "Continue with Google")
                            }

                            if (errorLog != null) {
                                Spacer(Modifier.height(24.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text("Error log", fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(6.dp))
                                        Text(errorLog ?: "")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        CoolanasAppRuntime.isForeground = true
        val connectivity = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        try {
            if (!networkCallbackRegistered) {
                connectivity.registerDefaultNetworkCallback(networkCallback)
                networkCallbackRegistered = true
            }
            val active = connectivity.activeNetwork
            val capabilities = active?.let(connectivity::getNetworkCapabilities)
            if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                signalNetworkSync()
            }
        } catch (error: SecurityException) {
            Log.w(LOG_TAG, "Network state is unavailable; WorkManager will retry queued sync.", error)
        }
    }

    override fun onStop() {
        CoolanasAppRuntime.isForeground = false
        // The account-owned Supabase terminal keeps running independently of the app process.
        // WorkManager only flushes completed analytics events already queued locally.
        activeUserId?.let { userId ->
            StudySyncWorker.enqueue(applicationContext, userId)
        }
        if (networkCallbackRegistered) {
            val connectivity = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            try {
                connectivity.unregisterNetworkCallback(networkCallback)
            } catch (_: Exception) {
                // The callback may already have been removed by the system.
            }
            networkCallbackRegistered = false
        }
        super.onStop()
    }

    private fun signalNetworkSync() {
        val userId = activeUserId ?: return
        StudySyncWorker.enqueue(applicationContext, userId)
        runOnUiThread { networkSyncSignal?.invoke() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeeplink(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_MESSAGES, false)) {
            messageNotificationNavigator?.invoke()
        }
    }

    private fun handleAuthDeeplink(callbackIntent: Intent) {
        try {
            SupabaseClientProvider.client.handleDeeplinks(
                callbackIntent,
                onError = { error ->
                    reportAuthError(
                        "Authentication callback failed: ${error.message ?: error::class.java.simpleName}",
                        error
                    )
                },
            )
        } catch (error: Exception) {
            reportAuthError(
                "Authentication callback failed: ${error.message ?: error::class.java.simpleName}",
                error
            )
        }
    }

    private fun reportAuthError(message: String, error: Throwable? = null) {
        if (error != null) {
            Log.e(LOG_TAG, message, error)
        } else {
            Log.e(LOG_TAG, message)
        }
        runOnUiThread {
            val reporter = errorReporter
            if (reporter != null) reporter(message) else pendingAuthError = message
        }
    }

    companion object {
        private const val LOG_TAG = "CoolanasAuth"
        const val EXTRA_OPEN_MESSAGES = "coolanas.open_messages"
    }
}
