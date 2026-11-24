package com.coolanas.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.handleDeeplinks
import io.github.jan.supabase.gotrue.gotrue
import io.github.jan.supabase.gotrue.providers.Google
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
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
                    try {
                        SupabaseClientProvider.client.gotrue.sessionStatus.collect { status ->
                            authenticated = status is SessionStatus.Authenticated
                        }
                    } catch (error: Exception) {
                        Log.e(LOG_TAG, "Unable to observe authentication state", error)
                        errorLog = error.message ?: error::class.java.simpleName
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("app-root"),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
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
                            Text(
                                text = "Login successful",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        } else {
                            Button(
                                onClick = {
                                    if (!isStartingLogin) {
                                        isStartingLogin = true
                                        errorLog = null
                                        scope.launch {
                                            try {
                                                SupabaseClientProvider.client.gotrue.loginWith(Google)
                                            } catch (error: Exception) {
                                                Log.e(LOG_TAG, "Google sign-in could not be started", error)
                                                errorLog = error.message ?: error::class.java.simpleName
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

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeeplink(intent)
    }

    private fun handleAuthDeeplink(callbackIntent: android.content.Intent) {
        try {
            SupabaseClientProvider.client.handleDeeplinks(callbackIntent)
        } catch (error: Exception) {
            Log.e(LOG_TAG, "Authentication callback failed", error)
        }
    }

    private companion object {
        const val LOG_TAG = "CoolanasAuth"
    }
}
