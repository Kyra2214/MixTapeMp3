package com.cdvirtual.app.feature.importer

import com.cdvirtual.app.core.common.AppResult
import java.net.URL

object UrlInput {
    fun parse(raw: String): AppResult<URL> {
        val text = raw.trim()
        if (text.isEmpty()) return AppResult.Failure("Cole um link para continuar")
        val withScheme = if (text.contains("://")) text else "https://$text"
        return try {
            val url = URL(withScheme)
            if (url.protocol != "http" && url.protocol != "https") {
                AppResult.Failure("Só links http/https são aceitos")
            } else if (url.host.isNullOrBlank() || !url.host.contains('.')) {
                AppResult.Failure("Link inválido")
            } else {
                AppResult.Success(url)
            }
        } catch (e: Exception) {
            AppResult.Failure("Link inválido")
        }
    }
}
