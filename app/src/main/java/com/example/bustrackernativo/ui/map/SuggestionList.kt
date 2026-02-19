package com.example.bustrackernativo.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class LineSuggestion(
    val line: String,
    val description: String
)


@Composable
fun SuggestionList(
    suggestions: List<LineSuggestion>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Só mostra o card se houver sugestões
    if (suggestions.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        LazyColumn {
            items(suggestions) { suggestion ->
                ListItem(
                    headlineContent = { Text(suggestion.line) },
                    supportingContent = { Text(suggestion.description) },
                    modifier = Modifier.clickable { onSelect(suggestion.line) }
                )
            }
        }
    }
}