package com.cdvirtual.app.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.compose.ui.platform.LocalContext
import android.webkit.WebView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cdvirtual.app.core.diagnostics.AppDiagnostics
import com.cdvirtual.app.feature.diagnostics.LogsScreen
import com.cdvirtual.app.feature.importer.ImporterViewModel
import com.cdvirtual.app.feature.importer.DownloadsScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.currentBackStackEntryAsState
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
import com.cdvirtual.app.feature.settings.SettingsScreen
import com.cdvirtual.app.feature.welcome.WelcomeScreen
import com.cdvirtual.app.feature.youtube.YouTubeScreen

/** Volta uma tela; se já estiver na raiz, não faz nada (evita tela em branco). */
private fun NavHostController.back() {
    if (previousBackStackEntry != null) popBackStack()
}

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.back() }
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
        composable(Destination.Library.route) { LibraryScreen(onBack = back) }
        composable(Destination.Downloads.route) { DownloadsScreen(vm = importerVm, onBack = back) }
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
                vm = importerVm,
                onBack = back
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
        composable(Destination.YouTube.route) {
            YouTubeScreen(onBack = back)
        }
        composable(Destination.CdEditor.route) { CdEditorScreen(onBack = back) }
        composable(Destination.Equalizer.route) { EqualizerScreen(onBack = back) }
        composable(Destination.Player.route) { PlayerScreen(onBack = back) }
        composable(Destination.Export.route) { ExportScreen(onBack = back) }
        composable(Destination.Settings.route) { SettingsScreen(onBack = back) }\n        composable(Destination.Logs.route) { LogsScreen(onBack = back) }
    }
}
