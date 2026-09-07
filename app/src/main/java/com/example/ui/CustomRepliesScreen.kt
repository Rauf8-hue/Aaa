package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.model.CustomReply
import com.example.model.MatchMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRepliesScreen(
    modifier: Modifier = Modifier
) {
    val app = QuantumBotApp.instance
    val coroutineScope = rememberCoroutineScope()
    val replies by app.customReplyRepository.allReplies.collectAsState(initial = emptyList())

    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<CustomReply?>(null) }
    var ruleToDelete by remember { mutableStateOf<CustomReply?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        if (replies.isEmpty()) {
            // Empty State
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Forum,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Custom Replies",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Add trigger keywords and predefined replies. Incoming messages matching these triggers will be answered automatically without calling Gemini AI.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = {
                        editingRule = null
                        showAddEditDialog = true
                    },
                    modifier = Modifier.testTag("add_first_rule_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Custom Rule")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Custom rules are evaluated first. If a rule matches, its reply is sent immediately without calling Gemini.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                items(replies, key = { it.id }) { rule ->
                    CustomReplyCard(
                        rule = rule,
                        onToggle = { isEnabled ->
                            coroutineScope.launch {
                                app.customReplyRepository.update(rule.copy(isEnabled = isEnabled))
                            }
                        },
                        onEdit = {
                            editingRule = rule
                            showAddEditDialog = true
                        },
                        onDelete = {
                            ruleToDelete = rule
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }

        // Floating Action Button to Add Rule
        FloatingActionButton(
            onClick = {
                editingRule = null
                showAddEditDialog = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_rule_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Rule")
        }

        // Add / Edit Dialog
        if (showAddEditDialog) {
            AddEditRuleDialog(
                rule = editingRule,
                onDismiss = { showAddEditDialog = false },
                onSave = { trigger, reply, matchMode, isEnabled ->
                    coroutineScope.launch {
                        if (editingRule == null) {
                            app.customReplyRepository.insert(
                                CustomReply(
                                    trigger = trigger,
                                    reply = reply,
                                    matchMode = matchMode.name,
                                    isEnabled = isEnabled
                                )
                            )
                        } else {
                            app.customReplyRepository.update(
                                editingRule!!.copy(
                                    trigger = trigger,
                                    reply = reply,
                                    matchMode = matchMode.name,
                                    isEnabled = isEnabled
                                )
                            )
                        }
                        showAddEditDialog = false
                    }
                }
            )
        }

        // Delete Confirmation Dialog
        ruleToDelete?.let { rule ->
            AlertDialog(
                onDismissRequest = { ruleToDelete = null },
                title = { Text("Delete Rule") },
                text = { Text("Are you sure you want to delete the rule for trigger '${rule.trigger}'?") },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                app.customReplyRepository.delete(rule)
                                ruleToDelete = null
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { ruleToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun CustomReplyCard(
    rule: CustomReply,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("rule_card_${rule.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Match Mode Badge
                val matchModeLabel = when (rule.matchMode) {
                    MatchMode.EXACT.name -> "Exact Match"
                    MatchMode.CONTAINS.name -> "Contains Word"
                    else -> "Case Insensitive"
                }

                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = matchModeLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = rule.isEnabled,
                        onCheckedChange = onToggle,
                        modifier = Modifier.testTag("rule_toggle_${rule.id}")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Rule",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Rule",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Trigger Display
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Trigger Message:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "\"${rule.trigger}\"",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Reply Display
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Reply,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Automated Reply:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = rule.reply,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRuleDialog(
    rule: CustomReply?,
    onDismiss: () -> Unit,
    onSave: (trigger: String, reply: String, matchMode: MatchMode, isEnabled: Boolean) -> Unit
) {
    var trigger by remember { mutableStateOf(rule?.trigger ?: "") }
    var reply by remember { mutableStateOf(rule?.reply ?: "") }
    var matchMode by remember {
        mutableStateOf(
            try {
                if (rule != null) MatchMode.valueOf(rule.matchMode) else MatchMode.CASE_INSENSITIVE
            } catch (_: Exception) {
                MatchMode.CASE_INSENSITIVE
            }
        )
    }
    var isEnabled by remember { mutableStateOf(rule?.isEnabled ?: true) }
    var matchModeMenuExpanded by remember { mutableStateOf(false) }
    var triggerError by remember { mutableStateOf(false) }
    var replyError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule == null) "Add Custom Reply" else "Edit Custom Reply") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Trigger Text Field
                OutlinedTextField(
                    value = trigger,
                    onValueChange = {
                        trigger = it
                        if (it.isNotBlank()) triggerError = false
                    },
                    label = { Text("Trigger Keyword / Sentence") },
                    placeholder = { Text("e.g. Hi, Pricing, Hours") },
                    isError = triggerError,
                    supportingText = {
                        if (triggerError) Text("Trigger cannot be empty", color = MaterialTheme.colorScheme.error)
                        else Text("What incoming WhatsApp text triggers this")
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rule_trigger_input")
                )

                // Reply Text Field
                OutlinedTextField(
                    value = reply,
                    onValueChange = {
                        reply = it
                        if (it.isNotBlank()) replyError = false
                    },
                    label = { Text("Predefined Reply") },
                    placeholder = { Text("e.g. Hello! How can I help you today?") },
                    isError = replyError,
                    supportingText = {
                        if (replyError) Text("Reply cannot be empty", color = MaterialTheme.colorScheme.error)
                        else Text("Exact reply to send back automatically")
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rule_reply_input")
                )

                // Match Mode Selector
                ExposedDropdownMenuBox(
                    expanded = matchModeMenuExpanded,
                    onExpandedChange = { matchModeMenuExpanded = !matchModeMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = matchMode.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Matching Mode") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = matchModeMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = matchModeMenuExpanded,
                        onDismissRequest = { matchModeMenuExpanded = false }
                    ) {
                        MatchMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(mode.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(mode.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                onClick = {
                                    matchMode = mode
                                    matchModeMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (trigger.isBlank()) {
                        triggerError = true
                        return@Button
                    }
                    if (reply.isBlank()) {
                        replyError = true
                        return@Button
                    }
                    onSave(trigger.trim(), reply.trim(), matchMode, isEnabled)
                },
                modifier = Modifier.testTag("save_rule_button")
            ) {
                Text(if (rule == null) "Create Rule" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
