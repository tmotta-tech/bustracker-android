package com.example.bustracker.data

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
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("longitude") val longitude: Double?,
    @SerializedName("velocidade") val velocidade: Double?,
    @SerializedName("datahora") val datahora: Long? // Often returns timestamp or string
)

// --- Internal App Model (Unified Bus) ---

data class Bus(
    val id: String,
    val linha: String, // The display line (e.g. "417T" or "343")
    val lat: Double,
    val lng: Double,
    val velocidade: Double,
    val tipo: BusType, // MUNICIPAL or INTERMUNICIPAL
    val sentido: String? = null // "IDA" or "VOLTA" (Only for Detro)
)

enum class BusType {
    MUNICIPAL, INTERMUNICIPAL
}
