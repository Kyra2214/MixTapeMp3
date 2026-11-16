package com.cdvirtual.app.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import android.webkit.WebView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdvirtual.app.feature.importer.ImporterViewModel
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cdvirtual.app.feature.browser.BrowserScreen
import com.cdvirtual.app.feature.cdeditor.CdEditorScreen
import com.cdvirtual.app.feature.equalizer.EqualizerScreen
import com.cdvirtual.app.feature.export.ExportScreen
import com.cdvirtual.app.feature.home.HomeScreen
import com.cdvirtual.app.feature.importer.ImporterScreen
import com.cdvirtual.app.feature.library.LibraryScreen
import com.cdvirtual.app.feature.player.PlayerScreen
import com.cdvirtual.app.feature.welcome.WelcomeScreen

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val browserWebView = remember { WebView(context) }
    // Escopo da Activity: a fila não morre quando a tela Importar sai da navegação.
    val importerVm: ImporterViewModel = viewModel()
    NavHost(navController = nav, startDestination = Destination.Welcome.route) {
        composable(Destination.Welcome.route) {
            WelcomeScreen(onFinished = {
                nav.navigate(Destination.Home.route) { popUpTo(Destination.Welcome.route) { inclusive = true } }
            })
        }
        composable(Destination.Home.route) { HomeScreen(onNavigate = { nav.navigate(it.route) }) }
        composable(Destination.Library.route) { LibraryScreen() }
        composable(
            route = "importer?url={url}&format={format}",
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("format") { type = NavType.StringType; defaultValue = "BEST" }
            )
        ) { entry ->
            ImporterScreen(
                initialUrl = entry.arguments?.getString("url"),
                preferredFormat = entry.arguments?.getString("format") ?: "BEST",
                vm = importerVm
            )
        }
        composable(Destination.Browser.route) {
            BrowserScreen(
                webView = browserWebView,
                vm = importerVm,
                onHome = {
                    nav.navigate(Destination.Home.route) {
                        popUpTo(Destination.Home.route) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Destination.CdEditor.route) { CdEditorScreen() }
        composable(Destination.Equalizer.route) { EqualizerScreen() }
        composable(Destination.Player.route) { PlayerScreen() }
        composable(Destination.Export.route) { ExportScreen() }
    }
}
