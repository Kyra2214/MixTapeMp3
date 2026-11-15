package com.cdvirtual.app.core.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Verificação rápida de conectividade (10.7). O app é offline-first: só a importação por link usa a internet, e todo o
 * resto (biblioteca, análise, EQ, editor de CD, geração do MP3, player, exportação) funciona sem conexão.
 */
object NetworkStatus {
    const val OFFLINE_MESSAGE =
        "Sem conexão com a internet. Conecte-se para importar por link; importar arquivos do aparelho e usar o resto do app funciona offline."

    /** Falso só quando se sabe que não há rede com acesso à internet; se não for possível saber, devolve verdadeiro e deixa a tentativa decidir. */
    fun isOnline(context: Context): Boolean = try {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (cm == null) true else {
            val net = cm.activeNetwork
            val caps = if (net == null) null else cm.getNetworkCapabilities(net)
            caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    } catch (_: Throwable) { true }
}
