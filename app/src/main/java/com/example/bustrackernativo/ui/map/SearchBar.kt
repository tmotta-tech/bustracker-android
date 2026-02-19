package com.example.bustrackernativo.ui.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun SearchBar(
    onAddLine: (String) -> Unit,
    onSearch: (String) -> Unit,
    suggestions: List<LineSuggestion>,
    onClearSuggestions: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val handleAdd = {
        if (query.isNotBlank()) {
            onAddLine(query.uppercase())
            query = ""
            onClearSuggestions()
            keyboardController?.hide()
        }
    }

    val handleSelect = { line: String ->
        onAddLine(line)
        query = ""
        onClearSuggestions()
        keyboardController?.hide()
        Unit
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .statusBarsPadding() // Avoid overlapping with status bar
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge, // Rounded Pill shape
            shadowElevation = 8.dp, // Slightly higher elevation for floating effect
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f) // Glassmorphism: Semi-transparent
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = query,
                    onValueChange = { 
                        query = it
                        onSearch(it)
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { 
                        Text(
                            "Buscar linha (ex: 343, 417T)", 
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        ) 
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { handleAdd() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ) // Clean look without underlines
                )

                if (query.isNotBlank()) {
                    IconButton(onClick = { 
                        query = "" 
                        onClearSuggestions()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            }
        }
        
        // Suggestion List Dropdown (Floating Effect)
        if (suggestions.isNotEmpty()) {
            SuggestionList(
                suggestions = suggestions,
                onSelect = handleSelect,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
