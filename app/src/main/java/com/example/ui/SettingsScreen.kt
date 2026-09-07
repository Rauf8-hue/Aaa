package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.QuantumBotApp
import com.example.model.GeminiResult
import com.example.model.ResponseLanguage
import com.example.model.ResponseLength
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = QuantumBotApp.instance
    val coroutineScope = rememberCoroutineScope()
    val settings by app.botSettingsRepository.settings.collectAsState()

    var apiKeyInput by remember { mutableStateOf("") }
    var isKeyHidden by remember { mutableStateOf(true) }
    var maskedKey by remember { mutableStateOf(app.secureApiKeyStorage.getMaskedApiKey()) }

    var isTestingKey by remember { mutableStateOf(false) }
    var keyTestResult by remember { mutableStateOf<GeminiResult?>(null) }

    var personalityText by remember(settings.aiPersonality) { mutableStateOf(settings.aiPersonality) }
    var languageDropdownExpanded by remember { mutableStateOf(false) }
    var lengthDropdownExpanded by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Gemini API Key Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "GEMINI API KEY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Current Key: $maskedKey",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = {
                        apiKeyInput = it
                        keyTestResult = null
                    },
                    label = { Text("Enter / Update Gemini API Key") },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    visualTransformation = if (isKeyHidden) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { isKeyHidden = !isKeyHidden }) {
                            Icon(
                                imageVector = if (isKeyHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Key Visibility"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (apiKeyInput.isNotBlank()) {
                                app.secureApiKeyStorage.saveApiKey(apiKeyInput.trim())
                                maskedKey = app.secureApiKeyStorage.getMaskedApiKey()
                                apiKeyInput = ""
                            }
                        },
                        enabled = apiKeyInput.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_api_key_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Key")
                    }

                    OutlinedButton(
                        onClick = {
                            isTestingKey = true
                            keyTestResult = null
                            coroutineScope.launch {
                                val testKey = apiKeyInput.ifBlank { null }
                                val result = app.geminiService.testConnection(testKey)
                                keyTestResult = result
                                isTestingKey = false
                            }
                        },
                        enabled = !isTestingKey && (apiKeyInput.isNotBlank() || app.secureApiKeyStorage.hasApiKey()),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_api_key_button")
                    ) {
                        if (isTestingKey) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Key")
                        }
                    }

                    if (app.secureApiKeyStorage.hasApiKey()) {
                        IconButton(
                            onClick = {
                                app.secureApiKeyStorage.removeApiKey()
                                maskedKey = app.secureApiKeyStorage.getMaskedApiKey()
                                apiKeyInput = ""
                                keyTestResult = null
                            }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove Key", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // Key Test Result Display
                keyTestResult?.let { result ->
                    Spacer(modifier = Modifier.height(10.dp))
                    when (result) {
                        is GeminiResult.Success -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = StatusSuccess.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("Connection Successful!", fontWeight = FontWeight.Bold, color = StatusSuccess, fontSize = 13.sp)
                                        Text("Verified in ${result.latencyMs}ms", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                        is GeminiResult.Error -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = StatusError.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = StatusError)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("Connection Failed", fontWeight = FontWeight.Bold, color = StatusError, fontSize = 13.sp)
                                        Text(result.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Gemini Model Selection
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "GEMINI MODEL",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                val availableModels = listOf("gemini-2.5-flash", "gemini-3.5-flash", "gemini-2.5-pro")

                ExposedDropdownMenuBox(
                    expanded = modelDropdownExpanded,
                    onExpandedChange = { modelDropdownExpanded = !modelDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = settings.geminiModel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Active Gemini Model") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false }
                    ) {
                        availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model) },
                                onClick = {
                                    app.botSettingsRepository.updateSettings(settings.copy(geminiModel = model))
                                    modelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // AI Personality & Instructions
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "AI PERSONALITY & INSTRUCTIONS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Quick Presets:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        "Support" to "You are a professional customer support assistant. Be polite, concise, and helpful.",
                        "Assistant" to "You are an executive personal assistant answering on my behalf. Keep it polite, brief, and inform them I will follow up soon.",
                        "Friendly" to "You are a warm, casual friend answering messages. Use conversational, friendly tone and emojis naturally."
                    )
                    presets.forEach { (name, prompt) ->
                        FilterChip(
                            selected = personalityText == prompt,
                            onClick = {
                                personalityText = prompt
                                app.botSettingsRepository.updateSettings(settings.copy(aiPersonality = prompt))
                            },
                            label = { Text(name, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = personalityText,
                    onValueChange = {
                        personalityText = it
                        app.botSettingsRepository.updateSettings(settings.copy(aiPersonality = it))
                    },
                    label = { Text("System Personality Prompt") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("personality_input")
                )
            }
        }

        // Language & Output Sizing
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "RESPONSE GENERATION RULES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Response Language Dropdown
                ExposedDropdownMenuBox(
                    expanded = languageDropdownExpanded,
                    onExpandedChange = { languageDropdownExpanded = !languageDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = settings.responseLanguage.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Response Language") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = languageDropdownExpanded,
                        onDismissRequest = { languageDropdownExpanded = false }
                    ) {
                        ResponseLanguage.values().forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang.label) },
                                onClick = {
                                    app.botSettingsRepository.updateSettings(settings.copy(responseLanguage = lang))
                                    languageDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Response Length Dropdown
                ExposedDropdownMenuBox(
                    expanded = lengthDropdownExpanded,
                    onExpandedChange = { lengthDropdownExpanded = !lengthDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = settings.responseLength.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Response Length") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = lengthDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = lengthDropdownExpanded,
                        onDismissRequest = { lengthDropdownExpanded = false }
                    ) {
                        ResponseLength.values().forEach { len ->
                            DropdownMenuItem(
                                text = { Text(len.label) },
                                onClick = {
                                    app.botSettingsRepository.updateSettings(settings.copy(responseLength = len))
                                    lengthDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Multi-turn context toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Conversation Context", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Remember recent message exchanges for coherent multi-turn replies",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.conversationContextEnabled,
                        onCheckedChange = {
                            app.botSettingsRepository.updateSettings(settings.copy(conversationContextEnabled = it))
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Activity logging toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Activity Logging", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            "Record sent messages, replies, and latency locally in database",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.isLoggingEnabled,
                        onCheckedChange = {
                            app.botSettingsRepository.updateSettings(settings.copy(isLoggingEnabled = it))
                        }
                    )
                }
            }
        }

        // Accessibility Setup Guide Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessibilityNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "ACCESSIBILITY SERVICE CONFIGURATION",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Android requires the user to manually enable AccessibilityService for Quantum Bot. " +
                            "This allows Quantum Bot to read incoming messages in WhatsApp and automatically type and send the generated replies.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { openAccessibilitySettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Open Android Accessibility Settings")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
