package com.example.modunote

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.modunote.data.local.NoteTemplateViewModel
import com.example.modunote.data.local.NoteViewModel
import com.example.modunote.data.local.TagViewModel
import com.example.modunote.ui.theme.ModuNoteTheme
import com.example.modunote.ui.theme.LocalEnergySavingActive
import com.example.modunote.ui.screens.BlockEditorScreen
import com.example.modunote.ui.screens.HomeScreen
import com.example.modunote.ui.screens.ChatScreen
import com.example.modunote.ui.screens.TemplateScreen
import org.osmdroid.config.Configuration

class MainActivity : FragmentActivity() {
    private var isBatterySaverActive by mutableStateOf(false)
    private var powerSaveReceiver: android.content.BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        isBatterySaverActive = powerManager.isPowerSaveMode

        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: android.content.Intent) {
                isBatterySaverActive = powerManager.isPowerSaveMode
            }
        }
        registerReceiver(receiver, android.content.IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        powerSaveReceiver = receiver

        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
        ReminderScheduler.createChannel(this)

        setContent {
            CompositionLocalProvider(LocalEnergySavingActive provides isBatterySaverActive) {
                ModuNoteTheme(
                    darkTheme = isSystemInDarkTheme() || isBatterySaverActive,
                    amoledMode = isBatterySaverActive
                ) {
                    val navController = rememberNavController()
                    val noteViewModel: NoteViewModel = viewModel()
                    val noteTemplateViewModel: NoteTemplateViewModel = viewModel()
                    val tagViewModel: TagViewModel = viewModel()
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        enterTransition = {
                            if (targetState.destination.route?.startsWith("editor") == true) {
                                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
                            } else {
                                fadeIn(animationSpec = tween(300))
                            }
                        },
                        exitTransition = {
                            if (targetState.destination.route?.startsWith("editor") == true) {
                                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300))
                            } else {
                                fadeOut(animationSpec = tween(300))
                            }
                        },
                        popEnterTransition = {
                            if (initialState.destination.route?.startsWith("editor") == true) {
                                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
                            } else {
                                fadeIn(animationSpec = tween(300))
                            }
                        },
                        popExitTransition = {
                            if (initialState.destination.route?.startsWith("editor") == true) {
                                slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300))
                            } else {
                                fadeOut(animationSpec = tween(300))
                            }
                        }
                    ) {
                        composable("home") {
                            HomeScreen(noteViewModel, tagViewModel, navController)
                        }
                        composable(
                            "editor/{noteId}",
                            arguments = listOf(navArgument("noteId") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val noteId = backStackEntry.arguments?.getInt("noteId") ?: -1
                            BlockEditorScreen(
                                noteId = noteId,
                                noteViewModel = noteViewModel,
                                noteTemplateViewModel = noteTemplateViewModel,
                                tagViewModel = tagViewModel,
                                onBack = { navController.popBackStack() },
                                onNavigateTo = { id -> navController.navigate("editor/$id") },
                                onNavigateToHome = { navController.popBackStack("home", false) }
                            )
                        }
                        composable("chat") {
                            ChatScreen(
                                noteViewModel = noteViewModel,
                                onNavigateTo = { id -> navController.navigate("editor/$id") },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("templates") {
                            TemplateScreen(
                                noteTemplateViewModel = noteTemplateViewModel,
                                navController = navController
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        powerSaveReceiver?.let { unregisterReceiver(it) }
    }
}
