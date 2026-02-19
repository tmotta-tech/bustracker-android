package com.example.bustrackernativo.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Helper para detectar qualidade da rede e adaptar comportamento do app.
 */
object NetworkHelper {
    
    enum class NetworkQuality {
        WIFI,           // WiFi - melhor qualidade
        CELLULAR_GOOD,  // 4G/LTE bom
        CELLULAR_POOR,  // 3G ou 4G fraco
        OFFLINE         // Sem conexão
    }
    
    /**
     * Retorna a qualidade atual da rede.
     */
    fun getNetworkQuality(context: Context): NetworkQuality {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return NetworkQuality.OFFLINE
        val capabilities = cm.getNetworkCapabilities(network) ?: return NetworkQuality.OFFLINE
        
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkQuality.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                // Verificar velocidade downstream estimada
                val downstreamKbps = capabilities.linkDownstreamBandwidthKbps
                if (downstreamKbps >= 10000) { // 10 Mbps ou mais
                    NetworkQuality.CELLULAR_GOOD
                } else {
                    NetworkQuality.CELLULAR_POOR
                }
            }
            else -> NetworkQuality.CELLULAR_POOR
        }
    }
    
    /**
     * Retorna o intervalo de atualização recomendado em milissegundos.
     */
    fun getRecommendedInterval(quality: NetworkQuality): Long {
        return when (quality) {
            NetworkQuality.WIFI -> 8_000L           // 8s - conexão rápida
            NetworkQuality.CELLULAR_GOOD -> 10_000L // 10s - 4G bom
            NetworkQuality.CELLULAR_POOR -> 20_000L // 20s - 3G/4G lento
            NetworkQuality.OFFLINE -> 60_000L       // 60s - só retry
        }
    }
    
    /**
     * Verifica se está online.
     */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
