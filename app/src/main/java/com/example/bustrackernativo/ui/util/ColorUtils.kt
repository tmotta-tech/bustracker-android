package com.example.bustrackernativo.ui.util

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Gera uma cor determinística com base no nome da linha de ônibus.
 * Isso garante que a mesma linha sempre terá a mesma cor.
 */
fun lineToColor(line: String): Color {
    // Usa o hashCode da string para gerar uma cor consistente.
    // Usamos abs() para garantir que o resultado seja positivo.
    val hash = abs(line.hashCode())
    val r = (hash and 0xFF0000) shr 16
    val g = (hash and 0x00FF00) shr 8
    val b = (hash and 0x0000FF)
    // Color(Int) interpreta como ARGB, então precisamos construir corretamente
    return Color(red = r / 255f, green = g / 255f, blue = b / 255f, alpha = 1f)
}