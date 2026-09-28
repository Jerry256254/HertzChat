package cz.kuclab.hertzchat.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import cz.kuclab.hertzchat.ui.call.CallScreen
import cz.kuclab.hertzchat.ui.chat.ChatScreen
import cz.kuclab.hertzchat.ui.chatlist.ChatListScreen
import cz.kuclab.hertzchat.ui.contacts.ContactsScreen
import cz.kuclab.hertzchat.ui.file.FileViewerScreen
import cz.kuclab.hertzchat.ui.groupchat.GroupChatScreen
import cz.kuclab.hertzchat.ui.assistant.HertzAssistantScreen
import cz.kuclab.hertzchat.ui.migration.QrExportScreen
import cz.kuclab.hertzchat.ui.migration.QrImportScreen
import cz.kuclab.hertzchat.ui.onboarding.OnboardingScreen
import cz.kuclab.hertzchat.ui.profile.ProfileScreen
import cz.kuclab.hertzchat.ui.settings.SettingsViewModel
import cz.kuclab.hertzchat.ui.update.UpdateAvailableDialog
import cz.kuclab.hertzchat.ui.settings.SettingsScreen

@Composable
fun HertzNavHost(viewModel: RootViewModel = hiltViewModel()) {
    val startDestination by viewModel.startDestination.collectAsState()
    val destination = startDestination ?: return

    val navController = rememberNavController()
    // A ringing call surfaces its screen from anywhere - chat list, a thread,
    // even a cold start through the notification tap.
    val incoming by viewModel.incomingCall.collectAsState()
    LaunchedEffect(incoming) {
        incoming?.let {
            if (navController.currentBackStackEntry?.destination?.route != Routes.CALL) {
                navController.navigate(Routes.call(it.contactId))
            }
        }
    }
    // One shared screen motion: the new surface glides in over the old one,
    // back pops the reverse. Subtle quarter-slide plus fade, never a full swap.
    NavHost(
        navController = navController,
        startDestination = destination,
        enterTransition = { slideInHorizontally(initialOffsetX = { it / 4 }) + fadeIn() },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut() },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }) + fadeIn() },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it / 4 }) + fadeOut() },
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Routes.CHAT_LIST) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onRestoreFromQr = { navController.navigate(Routes.QR_IMPORT) },
            )
        }
        composable(Routes.CHAT_LIST) {
            ChatListScreen(
                onOpenChat = { contactId -> navController.navigate(Routes.chat(contactId)) },
                onOpenGroup = { groupId -> navController.navigate(Routes.groupChat(groupId)) },
                onOpenContacts = { navController.navigate(Routes.CONTACTS) },
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAssistant = { navController.navigate(Routes.ASSISTANT_CHAT) },
            )
        }
        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("contactId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId").orEmpty()
            ChatScreen(
                contactId = contactId,
                onBack = { navController.popBackStack() },
                onOpenFile = { messageId -> navController.navigate(Routes.fileViewer(messageId)) },
                onOpenCall = { navController.navigate(Routes.call(contactId)) },
            )
        }
        composable(
            route = Routes.CALL,
            arguments = listOf(navArgument("contactId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("contactId").orEmpty()
            CallScreen(
                contactId = contactId,
                onDone = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.GROUP_CHAT,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId").orEmpty()
            GroupChatScreen(
                groupId = groupId,
                onBack = { navController.popBackStack() },
                onLeft = { navController.popBackStack() },
                onOpenFile = { messageId -> navController.navigate(Routes.fileViewer(messageId)) },
            )
        }
        composable(Routes.CONTACTS) {
            ContactsScreen(
                onBack = { navController.popBackStack() },
                onOpenChat = { contactId -> navController.navigate(Routes.chat(contactId)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ASSISTANT_CHAT) {
            HertzAssistantScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.FILE_VIEWER,
            arguments = listOf(navArgument("messageId") { type = NavType.StringType }),
        ) {
            FileViewerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PROFILE) {
            ProfileScreen(onBack = { navController.popBackStack() }, onOpenQrExport = { navController.navigate(Routes.QR_EXPORT) })
        }
        composable(Routes.QR_EXPORT) {
            QrExportScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.QR_IMPORT) {
            QrImportScreen(onDone = { navController.popBackStack() })
        }
    }

    // Cold-start update nudge, once per launch and only past onboarding.
    val startupUpdate by viewModel.startupUpdate.collectAsState()
    var updateDismissed by remember { mutableStateOf(false) }
    if (startupUpdate != null && !updateDismissed && destination == Routes.CHAT_LIST) {
        val settingsVm: SettingsViewModel = hiltViewModel()
        val updateState by settingsVm.updateCheckState.collectAsState()
        val ctx = LocalContext.current
        UpdateAvailableDialog(
            info = startupUpdate!!,
            updateState = updateState,
            onUpdate = {
                startupUpdate?.let { info ->
                    info.apkUrl?.let { settingsVm.downloadAndInstall(it, info.latestVersion) }
                }
            },
            onOpenReleasePage = {
                startupUpdate?.let { info ->
                    ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(info.releaseUrl)))
                }
            },
            onOpenInstallSettings = settingsVm::openInstallSettings,
            onDismiss = { updateDismissed = true },
        )
    }
}
