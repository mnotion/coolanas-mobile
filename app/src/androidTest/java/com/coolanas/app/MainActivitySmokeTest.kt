package com.coolanas.app

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
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
}
