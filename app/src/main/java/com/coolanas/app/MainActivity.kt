package com.coolanas.app

import android.os.Bundle
import android.util.Log
import android.content.Intent
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
import androidx.compose.runtime.getValue
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
import io.github.jan.supabase.auth.event.AuthEvent
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Google
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var errorReporter: ((String) -> Unit)? = null
    private var pendingAuthError: String? = null

    @OptIn(SupabaseExperimental::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthDeeplink(intent)
        setContent {
            CoolanasTheme {
                var authenticated by remember { mutableStateOf(false) }
                var isStartingLogin by remember { mutableStateOf(false) }
                var errorLog by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    errorReporter = { message -> errorLog = message }
                    pendingAuthError?.let {
                        errorLog = it
                        pendingAuthError = null
                    }
                    try {
                        SupabaseClientProvider.client.auth.sessionStatus.collect { status ->
                            authenticated = status is SessionStatus.Authenticated
                        }
                    } catch (error: Exception) {
                        reportAuthError("Could not read login state: ${error.message ?: error::class.java.simpleName}", error)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize().testTag("app-root"),
                    color = MaterialTheme.colorScheme.background,
                ) {
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

                        if (authenticated) {
                            Text("Login successful", style = MaterialTheme.typography.headlineSmall)
                        } else {
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeeplink(intent)
    }

    private fun handleAuthDeeplink(callbackIntent: Intent) {
        try {
            SupabaseClientProvider.client.handleDeeplinks(
                callbackIntent,
                onError = { error -> reportAuthError("Authentication callback failed: ${error.message ?: error::class.java.simpleName}", error) },
            )
        } catch (error: Exception) {
            reportAuthError("Authentication callback failed: ${error.message ?: error::class.java.simpleName}", error)
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

    private companion object {
        const val LOG_TAG = "CoolanasAuth"
    }
}
