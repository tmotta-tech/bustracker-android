package com.example.bustrackernativo.ui.map

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.bustrackernativo.ui.util.lineToColor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveLineChips(
    lines: List<String>,
    onRemoveLine: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (lines.isNotEmpty()) {
        FlowRow(
            modifier = modifier.padding(horizontal = 4.dp, vertical = 0.dp), // Minimal outer padding
        ) {
            lines.forEach { line ->
                val color = lineToColor(line)
                InputChip(
                    selected = true,
                    onClick = { onRemoveLine(line) },
                    label = { 
                        Text(
                            text = line,
                            style = MaterialTheme.typography.labelMedium, // Smaller text
                            color = Color.White
                        ) 
                    },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove",
                            tint = Color.White,
                            modifier = Modifier.padding(start = 2.dp).size(14.dp) // Smaller icon
                        )
                    },
                    colors = InputChipDefaults.inputChipColors(
                        selectedContainerColor = color,
                        selectedLabelColor = Color.White
                    ),
                    elevation = InputChipDefaults.inputChipElevation(elevation = 4.dp),
                    border = InputChipDefaults.inputChipBorder(
                        enabled = true,
                        selected = true,
                        borderColor = Color.White.copy(alpha = 0.5f),
                        borderWidth = 1.dp
                    ),
                    modifier = Modifier
                        .padding(end = 6.dp, bottom = 6.dp) 
                        .height(32.dp) // Slightly taller for touch target
                )
            }
        }
    }
}