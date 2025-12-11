package ro.jf.funds.client.android.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.benasher44.uuid.uuidFrom
import ro.jf.funds.client.android.ui.screen.FundListScreen
import ro.jf.funds.client.android.ui.screen.LoginScreen

@Composable
fun FundsApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onLoginSuccess = { userId ->
                    navController.navigate("funds/${userId}") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "funds/{userId}",
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userIdString = backStackEntry.arguments?.getString("userId")
            val userId = uuidFrom(userIdString!!)
            FundListScreen(userId = userId)
        }
    }
}
