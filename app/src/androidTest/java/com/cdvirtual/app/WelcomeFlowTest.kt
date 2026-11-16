package com.cdvirtual.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WelcomeFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun welcomeFlowOpensHomeScreen() {
        // A tela de boas-vindas avança sozinha após a animação; não depende de toque sintético
        // no emulador, que falhava antes mesmo de localizar o texto (que também estava divergente).
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Seu estúdio").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Seu estúdio").assertIsDisplayed()
        composeRule.onNodeWithText("Criar minha playlist").assertIsDisplayed()
    }
}
