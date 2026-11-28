package com.coolanas.app

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

class CoolanasLaunchTest {
    private val notificationPermission = GrantPermissionRule.grant(
        Manifest.permission.POST_NOTIFICATIONS
    )
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(notificationPermission).around(composeRule)

    @Test
    fun mainActivityShowsComposeRoot() {
        composeRule.onNodeWithTag("app-root").assertIsDisplayed()
    }
}
