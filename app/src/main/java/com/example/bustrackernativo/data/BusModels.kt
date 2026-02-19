package com.example.bustrackernativo.data

import com.google.gson.annotations.SerializedName

// --- Database Models (Mapped to bus_database.json) ---

data class BusDatabase(
    val metadata: ValidMetadata,
    val detro: Map<String, DetroLineConfig>,
    val rio: Map<String, RioLineConfig>
)

data class ValidMetadata(
    val generatedAt: String,
    val detroCount: Int,
    val rioCount: Int
)

data class DetroLineConfig(
    val nome: String,
    val guid: String,
    val idEmpresa: String,
    val linha: String // Internal ID (e.g. "2343")
)

data class RioLineConfig(
    val nome: String,
    val tipo: String
)

// --- API Response Models ---

// DETRO API Response
data class DetroBusResponse(
    @SerializedName("idVeiculo") val idVeiculo: String?,
    @SerializedName("gps") val gps: DetroGpsData?
)

data class DetroGpsData(
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("longitude") val longitude: Double?,
    @SerializedName("velocidade") val velocidade: Double?,
    @SerializedName("dataHora") val dataHora: String?
)

// RIO API Response (Data Rio)
data class RioBusResponse(
    @SerializedName("ordem") val ordem: String?,
    @SerializedName("linha") val linha: String?,
    @SerializedName("latitude") val latitude: String?,
    @SerializedName("longitude") val longitude: String?,
    @SerializedName("velocidade") val velocidade: String?,
    @SerializedName("datahora") val datahora: Long? // Often returns timestamp or string
)

// --- Internal App Model (Unified Bus) ---

data class BusInfo(
    val id: String,
    val linha: String, // The display line (e.g. "417T" or "343")
    val lat: Double,
    val lng: Double,
    val velocidade: Double,
    val tipo: BusTypeInfo, // MUNICIPAL or INTERMUNICIPAL
    val sentido: String? = null, // "IDA" or "VOLTA" (Only for Detro)
    val timestamp: Long = System.currentTimeMillis() // Epoch millis when data was received
)

// Extension function to calculate data "age" in seconds
fun BusInfo.getAgeSeconds(): Long = (System.currentTimeMillis() - timestamp) / 1000

// Extension function to get alpha based on freshness
// Fresh (<30s): 100% opaque, Stale (30s-2min): 70%, Old (>2min): 40%
fun BusInfo.getFreshnessAlpha(): Float = when {
    getAgeSeconds() < 30 -> 1.0f
    getAgeSeconds() < 120 -> 0.7f
    else -> 0.4f
}

enum class BusTypeInfo {
    MUNICIPAL, INTERMUNICIPAL
}
