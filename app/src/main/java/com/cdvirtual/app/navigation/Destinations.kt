package com.cdvirtual.app.navigation

enum class Destination(val route: String, val label: String) {
    Welcome("welcome", "Boas-vindas"),
    Home("home", "Início"),
    Library("library", "Biblioteca"),
    Importer("importer", "Importar"),
    Downloads("downloads", "Downloads"),
    YouTube("youtube", "Central de Vídeos YT"),
    Browser("browser", "Central de Áudios YT"),
    CdEditor("cdeditor", "Criar playlist"),
    Equalizer("equalizer", "Equalizador"),
    Player("player", "Player"),
    Export("export", "Exportar"),
    Settings("settings", "Configurações")
}
