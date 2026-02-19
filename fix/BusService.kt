package com.example.bustracker.api

import com.example.bustracker.data.DetroBusResponse
import com.example.bustracker.data.RioBusResponse
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


    // --- RIO API (Municipal / SPPO) ---
    // Example URL: https://dados.mobilidade.rio/gps/sppo
    
    @GET("gps/sppo")
    suspend fun getRioBuses(
        @Query("_t") timestamp: Long = System.currentTimeMillis()
    ): Response<List<RioBusResponse>>
}
