package com.synapsenotes.ai.feature.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.outlined.AutoAwesome
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiToolsBottomSheet(
    onAction: (AiAction) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = "AI Tools",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
            
            ListItem(
                headlineContent = { Text("Auto-Complete") },
                supportingContent = { Text("Continue writing from current text") },
                leadingContent = { Icon(Icons.Outlined.AutoAwesome, null) },
                modifier = Modifier.clickable { onAction(AiAction.AUTO_COMPLETE) }
            )
            
            ListItem(
                headlineContent = { Text("Summarize") },
                supportingContent = { Text("Create a summary of this note") },
                leadingContent = { Icon(Icons.Default.Summarize, null) },
                modifier = Modifier.clickable { onAction(AiAction.SUMMARIZE) }
            )
            
            ListItem(
                headlineContent = { Text("Rewrite & Polish") },
                supportingContent = { Text("Improve clarity and tone") },
                leadingContent = { Icon(Icons.Default.Refresh, null) },
                modifier = Modifier.clickable { onAction(AiAction.REWRITE) }
            )
            
            ListItem(
                headlineContent = { Text("Make Bullet Points") },
                supportingContent = { Text("Convert text to a list") },
                leadingContent = { Icon(Icons.Default.FormatListBulleted, null) },
                modifier = Modifier.clickable { onAction(AiAction.BULLET_POINTS) }
            )
        }
    }
}
