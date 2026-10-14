package com.karuhundeveloper.notification

import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import com.karuhundeveloper.notification.delivery.DeliveryScheduler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.karuhundeveloper.notification.ui.screen.AddRuleScreen
import com.karuhundeveloper.notification.ui.screen.HistoryScreen
import com.karuhundeveloper.notification.ui.screen.MainScreen
import com.karuhundeveloper.notification.ui.theme.NotificationInterceptorTheme

// ── Navigation destinations ───────────────────────────────────────────────────

private object Routes {
    const val MAIN     = "main"
    const val ADD_RULE = "add_rule"
    const val HISTORY  = "history/{ruleId}/{ruleName}"

    fun history(id: Int, name: String) = "history/$id/$name"
}

// ─────────────────────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            try {
                val scheduler = DeliveryScheduler(applicationContext)
                scheduler.installRecovery()
                scheduler.recover()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MainActivity", "Cannot recover delivery queue", e)
            }
        }
        enableEdgeToEdge()
        setContent {
            NotificationInterceptorTheme {
                AppNavGraph()
            }
        }
    }
}

@Composable
private fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.MAIN
    ) {
        composable(Routes.MAIN) {
            MainScreen(
                onAddRuleClick = { navController.navigate(Routes.ADD_RULE) },
                onRuleClick = { rule ->
                    navController.navigate(Routes.history(rule.id, rule.ruleName))
                }
            )
        }
        composable(Routes.ADD_RULE) {
            AddRuleScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.HISTORY,
            arguments = listOf(
                navArgument("ruleId") { type = NavType.IntType },
                navArgument("ruleName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val ruleId = backStackEntry.arguments?.getInt("ruleId") ?: 0
            val ruleName = backStackEntry.arguments?.getString("ruleName") ?: ""
            HistoryScreen(
                ruleId = ruleId,
                ruleName = ruleName,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
