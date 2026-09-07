package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.WhatsAppAccessibilityService
import com.example.ui.ActivityLogScreen
import com.example.ui.CustomRepliesScreen
import com.example.ui.MainScreen
import com.example.ui.SettingsScreen
import com.example.ui.TestAIScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

enum class AppDestination(val label: String, val icon: ImageVector, val testTag: String) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard, "tab_dashboard"),
    CUSTOM_RULES("Rules", Icons.Default.Forum, "tab_rules"),
    TEST_AI("Test AI", Icons.Default.Speed, "tab_test_ai"),
    ACTIVITY("Logs", Icons.Default.History, "tab_logs"),
    SETTINGS("Settings", Icons.Default.Settings, "tab_settings")
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val app = QuantumBotApp.instance
                val settings by app.botSettingsRepository.settings.collectAsState()
                val isAccessibilityConnected by WhatsAppAccessibilityService.isServiceConnected.collectAsState()

                var currentDestination by remember { mutableStateOf(AppDestination.DASHBOARD) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Live Status Dot
                                    val (statusColor, statusLabel) = when {
                                        !settings.isBotEnabled -> MaterialTheme.colorScheme.outline to "PAUSED"
                                        !isAccessibilityConnected -> StatusWarning to "AWAITING SERVICE"
                                        else -> StatusSuccess to "ONLINE"
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .clip(CircleShape)
                                            .background(statusColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "QUANTUM BOT",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 17.sp,
                                        letterSpacing = 1.2.sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = statusColor.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = statusLabel,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            AppDestination.values().forEach { destination ->
                                val selected = currentDestination == destination
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentDestination = destination },
                                    icon = {
                                        Icon(
                                            imageVector = destination.icon,
                                            contentDescription = destination.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = destination.label,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag(destination.testTag)
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Crossfade(
                        targetState = currentDestination,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        label = "screen_transition"
                    ) { dest ->
                        when (dest) {
                            AppDestination.DASHBOARD -> MainScreen(
                                onNavigateToCustomReplies = { currentDestination = AppDestination.CUSTOM_RULES },
                                onNavigateToSettings = { currentDestination = AppDestination.SETTINGS },
                                onNavigateToTestAI = { currentDestination = AppDestination.TEST_AI },
                                onNavigateToActivityLog = { currentDestination = AppDestination.ACTIVITY }
                            )
                            AppDestination.CUSTOM_RULES -> CustomRepliesScreen()
                            AppDestination.TEST_AI -> TestAIScreen()
                            AppDestination.ACTIVITY -> ActivityLogScreen()
                            AppDestination.SETTINGS -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }
}
