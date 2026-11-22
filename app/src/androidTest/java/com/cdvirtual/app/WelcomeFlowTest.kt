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
        // O texto de boas-vindas também existe na tela de abertura.
        // Esperamos pelo cartão exclusivo da Home para evitar falso positivo.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Central de Vídeos YT").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Central de Vídeos YT").assertIsDisplayed()
        composeRule.onNodeWithText("Central de Áudios YT").assertIsDisplayed()
    }

    @Test
    fun youtubeFeatureOpensFromHome() {
        // Aguarda a saída da tela de boas-vindas e a composição real da Home.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Central de Vídeos YT").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Central de Vídeos YT").performClick()
        composeRule.onNodeWithText("Buscar").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Pesquisar vídeos").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Pesquisar vídeos").assertIsDisplayed()
        composeRule.onNodeWithText("Pular trechos patrocinados").assertIsDisplayed()
    }
}
