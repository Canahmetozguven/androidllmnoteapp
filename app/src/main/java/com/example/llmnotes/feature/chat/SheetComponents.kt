package com.example.llmnotes.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.llmnotes.domain.model.Note
import com.example.llmnotes.domain.repository.ChatSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryBottomSheet(
    sessions: List<ChatSession>,
    onSessionClick: (ChatSession) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = "Chat History",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn {
                items(sessions) { session ->
                    ListItem(
                        headlineContent = { Text(session.title) },
                        supportingContent = { 
                            Text(SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(session.updatedAt)))
                        },
                        leadingContent = {
                            Icon(Icons.Default.History, null)
                        },
                        modifier = Modifier.clickable { onSessionClick(session) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextSelectionBottomSheet(
    allNotes: List<Note>,
    selectedNotes: List<Note>,
    onNoteToggle: (Note) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = "Select Context",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn {
                items(allNotes) { note ->
                    val isSelected = selectedNotes.any { it.id == note.id }
                    ListItem(
                        headlineContent = { Text(note.title.ifEmpty { "Untitled" }) },
                        leadingContent = {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { onNoteToggle(note) }
                            )
                        },
                        modifier = Modifier.clickable { onNoteToggle(note) }
                    )
                }
            }
        }
    }
}
