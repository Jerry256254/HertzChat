package cz.kuclab.hertzchat.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import cz.kuclab.hertzchat.ui.chat.ChatScreen
import cz.kuclab.hertzchat.ui.chatlist.ChatListScreen
import cz.kuclab.hertzchat.ui.contacts.ContactsScreen
import cz.kuclab.hertzchat.ui.migration.QrExportScreen
import cz.kuclab.hertzchat.ui.migration.QrImportScreen
import cz.kuclab.hertzchat.ui.mistral.AssistantChatScreen
import cz.kuclab.hertzchat.ui.onboarding.OnboardingScreen
import cz.kuclab.hertzchat.ui.profile.ProfileScreen
import cz.kuclab.hertzchat.ui.settings.SettingsScreen

@Composable
fun HertzNavHost(viewModel: RootViewModel = hiltViewModel()) {
    val startDestination by viewModel.startDestination.collectAsState()
    val destination = startDestination ?: return

    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = destination) {
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
            ChatScreen(contactId = contactId, onBack = { navController.popBackStack() })
        }
        composable(Routes.CONTACTS) {
            ContactsScreen(
                onOpenChat = { contactId -> navController.navigate(Routes.chat(contactId)) },
                onOpenAssistant = { navController.navigate(Routes.ASSISTANT_CHAT) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen()
        }
        composable(Routes.ASSISTANT_CHAT) {
            AssistantChatScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PROFILE) {
            ProfileScreen(onOpenQrExport = { navController.navigate(Routes.QR_EXPORT) })
        }
        composable(Routes.QR_EXPORT) {
            QrExportScreen(onDone = { navController.popBackStack() })
        }
        composable(Routes.QR_IMPORT) {
            QrImportScreen(onDone = { navController.popBackStack() })
        }
    }
}
