package ro.jf.funds.client.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ro.jf.funds.client.android.ui.screen.fundlist.FundListScreen
import ro.jf.funds.client.android.ui.screen.login.LoginScreen

@Composable
fun FundsApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("fundList") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("fundList") {
            FundListScreen()
        }
    }
}
