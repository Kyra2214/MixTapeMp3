package com.cdvirtual.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        // A tela inicial avança automaticamente após a animação de boas-vindas.
        // Validamos o título do card principal, que identifica a Home e fica na área visível,
        // sem depender de toque sintético nem de itens que podem ficar abaixo da dobra.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Seu som. Seu jeito.").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Seu som. Seu jeito.").assertIsDisplayed()
    }
    @Test
    fun youtubeFeatureOpensFromHome() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Seu som. Seu jeito.").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Central de Vídeos YT").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Pesquisar vídeos").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Pesquisar vídeos").assertIsDisplayed()
        composeRule.onNodeWithText("Pular trechos patrocinados").assertIsDisplayed()
    }

}
