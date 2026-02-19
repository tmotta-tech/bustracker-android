package com.example.bustrackernativo.data

import com.example.bustrackernativo.data.DetroBusResponse
import com.example.bustrackernativo.data.RioBusResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface BusService {

    // --- DETRO API (Intermunicipal) ---
    // Example URL: https://appbus.exall-host.com.br/ObterUltimasCoordenadasVeiculos.php?...
    
    @GET("ObterUltimasCoordenadasVeiculos.php")
    suspend fun getDetroBuses(
        @Query("guidIdentificacao") guid: String,
        @Query("idEmpresa") idEmpresa: String,
        @Query("linha") linha: String,
        @Query("sentido") sentido: Int, // 1 for IDA, 2 for VOLTA
        @Query("_t") timestamp: Long = System.currentTimeMillis()
    ): Response<List<DetroBusResponse>>


    // --- RIO API (Municipal / SPPO) via Cloudflare Proxy ---
    // Proxy URL: https://busapp.thiago-info-2c9.workers.dev/?linha=X
    // Returns only matching buses (fast! ~1-3 seconds vs 20+ minutes)
    
    @GET("/")  // Proxy uses root path
    suspend fun getRioBuses(
        @Query("linha") linha: String? = null,
        @Query("_t") timestamp: Long = System.currentTimeMillis()
    ): Response<List<RioBusResponse>>
}
