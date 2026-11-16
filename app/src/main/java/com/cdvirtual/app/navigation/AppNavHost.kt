package com.cdvirtual.app.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
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
                preferredFormat = entry.arguments?.getString("format") ?: "BEST"
            )
        }
        composable(Destination.Browser.route) {
            BrowserScreen { url, format ->
                nav.navigate("importer?url=${Uri.encode(url)}&format=${Uri.encode(format)}")
            }
        }
        composable(Destination.CdEditor.route) { CdEditorScreen() }
        composable(Destination.Equalizer.route) { EqualizerScreen() }
        composable(Destination.Player.route) { PlayerScreen() }
        composable(Destination.Export.route) { ExportScreen() }
    }
}
