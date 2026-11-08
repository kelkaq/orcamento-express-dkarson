package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.billing.PlanTier
import com.example.ui.screens.about.AboutScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Regressão: versão e privacidade devem ser fáceis de encontrar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AboutAndPlansUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `tela sobre mostra versão real e não divulga links de servidor ausente`() {
        compose.setContent {
            MaterialTheme {
                AboutScreen(
                    tier = PlanTier.FREE,
                    isDemo = false,
                    onlineConfigured = false,
                    onBack = {},
                    onOpenAccount = {},
                    onOpenPlan = {}
                )
            }
        }
        compose.onNodeWithTag("about_screen").assertIsDisplayed()
        compose.onNodeWithTag("about_app_version").assertIsDisplayed()
        compose.onNodeWithTag("about_privacy").assertIsNotEnabled()
        compose.onNodeWithTag("about_account_deletion").assertIsNotEnabled()
    }
}
