package com.vozatexto.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vozatexto.app.R
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: TranscriptionViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var fileToView by remember { mutableStateOf<File?>(null) }
    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) vm.startListening()
        else scope.launch {
            snackbarHostState.showSnackbar(context.getString(R.string.permission_required))
        }
    }

    // Show snackbar messages from ViewModel
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.snackbarShown()
        }
    }

    // File viewer dialog
    fileToView?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToView = null },
            title = { Text(file.name, style = MaterialTheme.typography.labelMedium) },
            text = {
                Text(
                    file.readText(),
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { fileToView = null }) { Text("Cerrar") }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mic button
            val isListening = state.status == Status.LISTENING
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        color = if (isListening) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
                    .clickable {
                        if (isListening) {
                            vm.stopListening()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = stringResource(if (isListening) R.string.mic_stop else R.string.mic_start),
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            // Status
            val statusText = when (state.status) {
                Status.READY -> stringResource(R.string.status_ready)
                Status.LISTENING -> stringResource(R.string.status_listening)
                Status.PROCESSING -> stringResource(R.string.status_processing)
                Status.ERROR -> stringResource(R.string.status_ready)
            }
            Text(statusText, style = MaterialTheme.typography.bodyMedium,
                color = if (state.status == Status.LISTENING) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant)

            // Transcription box
            val displayText = buildString {
                append(state.transcription)
                if (state.partialText.isNotEmpty()) {
                    if (state.transcription.isNotEmpty()) append(" ")
                    append(state.partialText)
                }
            }
            OutlinedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    if (displayText.isEmpty()) {
                        Text(
                            stringResource(R.string.hint_transcription),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    } else {
                        Text(
                            displayText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val text = state.transcription.trim()
                        if (text.isEmpty()) {
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.nothing_to_copy)) }
                        } else {
                            clipboard.setText(AnnotatedString(text))
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.copied)) }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_copy))
                }
                OutlinedButton(
                    onClick = { vm.save() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_save))
                }
                OutlinedButton(
                    onClick = { vm.clearTranscription() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_clear))
                }
            }

            // Saved files history
            HorizontalDivider()
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleSmall)
            if (state.savedFiles.isEmpty()) {
                Text(
                    stringResource(R.string.no_history),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                    items(state.savedFiles) { file ->
                        ListItem(
                            headlineContent = {
                                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall)
                            },
                            modifier = Modifier.clickable { fileToView = file }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
