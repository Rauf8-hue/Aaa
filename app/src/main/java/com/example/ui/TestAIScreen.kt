package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.QuantumBotApp
import com.example.model.GeminiResult
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TestAIScreen(
    modifier: Modifier = Modifier
) {
    val app = QuantumBotApp.instance
    val coroutineScope = rememberCoroutineScope()
    val settings by app.botSettingsRepository.settings.collectAsState()

    var testInput by remember { mutableStateOf("Hi! What services do you offer and what are your working hours?") }
    var isPingLoading by remember { mutableStateOf(false) }
    var isReplyLoading by remember { mutableStateOf(false) }
    var testType by remember { mutableStateOf("SIMULATION") } // "PING" or "SIMULATION"
    var testResult by remember { mutableStateOf<GeminiResult?>(null) }
    var testStage by remember { mutableStateOf("") }

    val hasApiKey = app.secureApiKeyStorage.hasApiKey()
    val maskedApiKey = app.secureApiKeyStorage.getMaskedApiKey()

    val quickPrompts = listOf(
        "What services do you offer?",
        "Are you open on weekends?",
        "Mujhe pricing jaanni hai",
        "Can I speak with a human?",
        "Where are you located?"
    )

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "LIVE AI DIAGNOSTICS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Test Gemini API & Pipeline",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Real Gemini requests independent from WhatsApp using the shared GeminiService.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. API Key Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("api_key_status_card"),
            colors = CardDefaults.cardColors(
                containerColor = if (hasApiKey) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                }
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = if (hasApiKey) MaterialTheme.colorScheme.primary else StatusError,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (hasApiKey) "Gemini API Key Configured" else "Gemini API Key Missing",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (hasApiKey) MaterialTheme.colorScheme.onSurface else StatusError
                        )
                        Text(
                            text = if (hasApiKey) "$maskedApiKey  •  Model: ${settings.geminiModel}" else "Go to Settings tab to enter your Gemini API key",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = if (hasApiKey) StatusSuccess.copy(alpha = 0.15f) else StatusError.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (hasApiKey) "READY" else "ACTION NEEDED",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasApiKey) StatusSuccess else StatusError
                    )
                }
            }
        }

        // 3. Quick Connection Ping Test
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "STEP 1: API AUTHENTICATION & PING",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Sends a minimal 1-token request directly to Gemini to verify API authentication and server response latency.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        isPingLoading = true
                        testType = "PING"
                        testStage = "Sending ping request to Gemini API..."
                        testResult = null
                        coroutineScope.launch {
                            val res = app.geminiService.testConnection(model = settings.geminiModel)
                            testResult = res
                            testStage = if (res is GeminiResult.Success) "Ping successful" else "Ping failed"
                            isPingLoading = false
                        }
                    },
                    enabled = !isPingLoading && !isReplyLoading && hasApiKey,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ping_gemini_button")
                ) {
                    if (isPingLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying API Key & Connectivity...")
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Gemini Connection (Ping)")
                    }
                }
            }
        }

        // 4. WhatsApp Reply Simulation Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "STEP 2: SIMULATE WHATSAPP AI REPLY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = testInput,
                    onValueChange = { testInput = it },
                    label = { Text("Incoming WhatsApp Message") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_message_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Quick Test Prompts:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPrompts.forEach { prompt ->
                        FilterChip(
                            selected = testInput == prompt,
                            onClick = { testInput = prompt },
                            label = { Text(prompt, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (testInput.isNotBlank()) {
                            isReplyLoading = true
                            testType = "SIMULATION"
                            testStage = "Request started: formatting prompt, personality & rules..."
                            testResult = null
                            coroutineScope.launch {
                                val res = app.geminiService.generateReply(
                                    incomingMessage = testInput,
                                    settings = settings
                                )
                                testResult = res
                                testStage = if (res is GeminiResult.Success) "Response extracted & verified" else "Request failed"
                                isReplyLoading = false
                            }
                        }
                    },
                    enabled = !isReplyLoading && !isPingLoading && testInput.isNotBlank() && hasApiKey,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("execute_test_button")
                ) {
                    if (isReplyLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Querying Gemini API...")
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulate AI WhatsApp Reply")
                    }
                }
            }
        }

        // 5. Diagnostics & Results Card
        testResult?.let { res ->
            when (res) {
                is GeminiResult.Success -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_success_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = StatusSuccess,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (testType == "PING") "CONNECTION TEST PASSED" else "AI RESPONSE GENERATED",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = StatusSuccess,
                                        letterSpacing = 1.sp
                                    )
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${res.latencyMs} ms",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Diagnostic Pipeline Stepper
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "DIAGNOSTIC TRACE:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("✓ API Key: Loaded & authenticated ($maskedApiKey)", fontSize = 11.sp)
                                    Text("✓ Model: ${settings.geminiModel}", fontSize = 11.sp)
                                    Text("✓ HTTP Status: 200 OK (${res.latencyMs}ms)", fontSize = 11.sp)
                                    Text("✓ Response Parsing: Text extracted successfully", fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Simulated Output Bubble
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (testType == "PING") "Gemini Response:" else "Quantum Bot (WhatsApp Output):",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = res.text,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
                is GeminiResult.Error -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_error_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = StatusError,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "REQUEST FAILED",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = StatusError,
                                    letterSpacing = 1.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = res.message,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )

                            if (res.latencyMs > 0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Request latency: ${res.latencyMs} ms",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Troubleshooting recommendations
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "TROUBLESHOOTING CHECKLIST:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("• If HTTP 400: Confirm your API key has no trailing whitespace.", fontSize = 11.sp)
                                    Text("• If HTTP 401/403: Re-copy your key from Google AI Studio.", fontSize = 11.sp)
                                    Text("• If HTTP 404: In Settings, select 'gemini-2.5-flash'.", fontSize = 11.sp)
                                    Text("• If HTTP 429: Rate limit hit. Free tier limits apply.", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

