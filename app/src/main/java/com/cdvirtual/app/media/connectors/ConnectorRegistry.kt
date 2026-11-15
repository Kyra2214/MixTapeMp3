package com.cdvirtual.app.media.connectors

class ConnectorRegistry(private val connectors: List<SourceConnector>) {
    fun forPlatform(platform: Platform): SourceConnector? = connectors.firstOrNull { it.supports(platform) }

    companion object {
        val default = ConnectorRegistry(listOf(DirectLinkConnector(), RestrictedPlatformConnector()))
    }
}
