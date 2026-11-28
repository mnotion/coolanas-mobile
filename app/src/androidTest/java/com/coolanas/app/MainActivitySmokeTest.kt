package com.coolanas.app

import android.Manifest
import android.app.NotificationManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.rules.RuleChain

@RunWith(JUnit4::class)
class MainActivitySmokeTest {
    private val notificationPermission = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS
    )
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(notificationPermission).around(composeRule)

    @Test
    fun appLaunchesAndShowsGoogleSignIn() {
        composeRule.waitUntil(timeoutMillis = 15_000L) {
            composeRule.onAllNodesWithText("Continue with Google").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("app-root").assertIsDisplayed()
        composeRule.onNodeWithText("Continue with Google").assertIsDisplayed()
    }

    @Test
    fun notificationChannelsAndMessageNotificationWork() {
        val context = composeRule.activity
        CoolanasNotifications.createChannels(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(manager.getNotificationChannel(CoolanasNotifications.TIMER_CHANNEL))
        assertNotNull(manager.getNotificationChannel(CoolanasNotifications.MESSAGE_CHANNEL))

        val senderId = "smoke-test-sender"
        val notificationId = 5000 + Math.floorMod(senderId.hashCode(), 100000)
        CoolanasNotifications.notifyMessage(context, "Smoke test", "New message", senderId)
        composeRule.waitUntil(5_000L) {
            manager.activeNotifications.any { it.id == notificationId }
        }
        assertTrue(manager.activeNotifications.any { it.id == notificationId })
        manager.cancel(notificationId)
    }

    @Test
    fun foregroundTimerServiceShowsAndRemovesChronometerNotification() {
        val context = composeRule.activity
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            StudyTimerNotificationService.show(context, 65_000L, "Maths")
            composeRule.waitUntil(10_000L) {
                manager.activeNotifications.any { it.id == CoolanasNotifications.TIMER_NOTIFICATION_ID }
            }
            assertTrue(manager.activeNotifications.any { it.id == CoolanasNotifications.TIMER_NOTIFICATION_ID })
        } finally {
            StudyTimerNotificationService.hide(context)
            CoolanasNotifications.clearTimer(context)
        }
    }
}
