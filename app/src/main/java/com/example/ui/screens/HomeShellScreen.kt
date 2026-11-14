package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CallSession
import com.example.data.remote.MediaBlobService
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.MainTab
import com.example.util.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeShellScreen(
    homeViewModel: HomeViewModel,
    mediaService: MediaBlobService,
    isOnline: Boolean,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    activeCall: CallSession?,
    onOpenNewChat: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onStartVoiceCall: (uid: String, name: String, phone: String?) -> Unit,
    onStartVideoCall: (uid: String, name: String, phone: String?) -> Unit,
    onReturnToActiveCall: () -> Unit,
    onOpenAdminPortal: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val selectedTab by homeViewModel.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by homeViewModel.searchQuery.collectAsStateWithLifecycle()
    val feedback by homeViewModel.feedback.collectAsStateWithLifecycle()
    val uploadProgress by homeViewModel.uploadProgress.collectAsStateWithLifecycle()
    val notifications by homeViewModel.notifications.collectAsStateWithLifecycle()
    val globalSettings by homeViewModel.globalSettings.collectAsStateWithLifecycle()
    val conversations by homeViewModel.conversations.collectAsStateWithLifecycle()
    val chatPrefs by homeViewModel.chatPrefs.collectAsStateWithLifecycle()

    var showSearchBar by remember { mutableStateOf(false) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedback) {
        val msg = feedback
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            homeViewModel.clearFeedback()
        }
    }

    val unreadChatsCount = remember(conversations, chatPrefs) {
        conversations.count { conv -> (chatPrefs[conv.id]?.unreadCount ?: 0) > 0 }
    }
    val unreadNotifCount = remember(notifications) {
        notifications.count { !it.isRead }
    }

    if (showNotificationsSheet) {
        NotificationsSheetDialog(
            notifications = notifications,
            onMarkRead = homeViewModel::markNotificationRead,
            onOpenConversation = { convId ->
                showNotificationsSheet = false
                onOpenConversation(convId)
            },
            onDismiss = { showNotificationsSheet = false }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(modifier = Modifier.fillMaxHeight()) {
                    Spacer(Modifier.height(12.dp))
                    NavigationRailItem(
                        selected = selectedTab == MainTab.CHATS,
                        onClick = { homeViewModel.selectTab(MainTab.CHATS) },
                        icon = { Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "Chats") },
                        label = { Text("Chats") }
                    )
                    NavigationRailItem(
                        selected = selectedTab == MainTab.UPDATES,
                        onClick = { homeViewModel.selectTab(MainTab.UPDATES) },
                        icon = { Icon(Icons.Rounded.Update, contentDescription = "Updates") },
                        label = { Text("Updates") }
                    )
                    NavigationRailItem(
                        selected = selectedTab == MainTab.COMMUNITIES,
                        onClick = { homeViewModel.selectTab(MainTab.COMMUNITIES) },
                        icon = { Icon(Icons.Rounded.Groups, contentDescription = "Communities") },
                        label = { Text("Communities") }
                    )
                    NavigationRailItem(
                        selected = selectedTab == MainTab.CALLS,
                        onClick = { homeViewModel.selectTab(MainTab.CALLS) },
                        icon = { Icon(Icons.Rounded.Call, contentDescription = "Calls") },
                        label = { Text("Calls") }
                    )
                    NavigationRailItem(
                        selected = selectedTab == MainTab.SETTINGS,
                        onClick = { homeViewModel.selectTab(MainTab.SETTINGS) },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }

            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    Column {
                        TopAppBar(
                            title = {
                                Text(
                                    text = when (selectedTab) {
                                        MainTab.CHATS -> "SEPTIS"
                                        MainTab.UPDATES -> "Updates"
                                        MainTab.COMMUNITIES -> "Communities"
                                        MainTab.CALLS -> "Calls"
                                        MainTab.SETTINGS -> "Settings"
                                    },
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            actions = {
                                if (!isOnline) {
                                    Icon(
                                        Icons.Rounded.WifiOff,
                                        contentDescription = "Offline",
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        showSearchBar = !showSearchBar
                                        if (!showSearchBar) homeViewModel.updateSearchQuery("")
                                    },
                                    modifier = Modifier.testTag("home_search_toggle")
                                ) {
                                    Icon(Icons.Rounded.Search, contentDescription = "Search")
                                }
                                IconButton(
                                    onClick = { showNotificationsSheet = true },
                                    modifier = Modifier.testTag("home_notifications_button")
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (unreadNotifCount > 0) {
                                                Badge { Text(unreadNotifCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Rounded.Notifications, contentDescription = "Notifications")
                                    }
                                }
                            }
                        )

                        AnimatedVisibility(visible = showSearchBar) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = homeViewModel::updateSearchQuery,
                                placeholder = { Text("Search chats, contacts, statuses, calls...") },
                                singleLine = true,
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { homeViewModel.updateSearchQuery("") }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("home_search_input")
                            )
                        }

                        if (uploadProgress != null) {
                            LinearProgressIndicator(
                                progress = { uploadProgress ?: 0f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (activeCall != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onReturnToActiveCall() }
                            ) {
                                Text(
                                    text = "Active call in progress — Tap to return",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        if (globalSettings.maintenanceMode || globalSettings.appBannerMessage.isNotBlank()) {
                            Surface(
                                color = if (globalSettings.maintenanceMode) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (globalSettings.maintenanceMode) {
                                        "SEPTIS Maintenance Notice: ${globalSettings.appBannerMessage.ifBlank { "Some actions may be restricted." }}"
                                    } else {
                                        globalSettings.appBannerMessage
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == MainTab.CHATS,
                                onClick = { homeViewModel.selectTab(MainTab.CHATS) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (unreadChatsCount > 0) Badge { Text(unreadChatsCount.toString()) }
                                        }
                                    ) {
                                        Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "Chats")
                                    }
                                },
                                label = { Text("Chats") },
                                modifier = Modifier.testTag("nav_tab_chats")
                            )
                            NavigationBarItem(
                                selected = selectedTab == MainTab.UPDATES,
                                onClick = { homeViewModel.selectTab(MainTab.UPDATES) },
                                icon = { Icon(Icons.Rounded.Update, contentDescription = "Updates") },
                                label = { Text("Updates") },
                                modifier = Modifier.testTag("nav_tab_updates")
                            )
                            NavigationBarItem(
                                selected = selectedTab == MainTab.COMMUNITIES,
                                onClick = { homeViewModel.selectTab(MainTab.COMMUNITIES) },
                                icon = { Icon(Icons.Rounded.Groups, contentDescription = "Communities") },
                                label = { Text("Communities") },
                                modifier = Modifier.testTag("nav_tab_communities")
                            )
                            NavigationBarItem(
                                selected = selectedTab == MainTab.CALLS,
                                onClick = { homeViewModel.selectTab(MainTab.CALLS) },
                                icon = { Icon(Icons.Rounded.Call, contentDescription = "Calls") },
                                label = { Text("Calls") },
                                modifier = Modifier.testTag("nav_tab_calls")
                            )
                            NavigationBarItem(
                                selected = selectedTab == MainTab.SETTINGS,
                                onClick = { homeViewModel.selectTab(MainTab.SETTINGS) },
                                icon = { Icon(Icons.Rounded.Settings, contentDescription = "Settings") },
                                label = { Text("Settings") },
                                modifier = Modifier.testTag("nav_tab_settings")
                            )
                        }
                    }
                },
                floatingActionButton = {
                    if (selectedTab == MainTab.CHATS || selectedTab == MainTab.CALLS) {
                        FloatingActionButton(
                            onClick = onOpenNewChat,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("fab_new_chat")
                        ) {
                            Icon(Icons.Rounded.AddComment, contentDescription = "New Chat")
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    when (selectedTab) {
                        MainTab.CHATS -> ChatsTabScreen(
                            homeViewModel = homeViewModel,
                            mediaService = mediaService,
                            onOpenConversation = onOpenConversation,
                            onOpenNewChat = onOpenNewChat
                        )
                        MainTab.UPDATES -> UpdatesTabScreen(
                            homeViewModel = homeViewModel,
                            mediaService = mediaService
                        )
                        MainTab.COMMUNITIES -> CommunitiesTabScreen(
                            homeViewModel = homeViewModel,
                            mediaService = mediaService,
                            onOpenConversation = onOpenConversation
                        )
                        MainTab.CALLS -> CallsTabScreen(
                            homeViewModel = homeViewModel,
                            mediaService = mediaService,
                            onStartVoiceCall = onStartVoiceCall,
                            onStartVideoCall = onStartVideoCall
                        )
                        MainTab.SETTINGS -> SettingsTabScreen(
                            homeViewModel = homeViewModel,
                            mediaService = mediaService,
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            onOpenAdminPortal = onOpenAdminPortal,
                            onLogoutClick = onLogoutClick
                        )
                    }
                }
            }
        }
    }
}
