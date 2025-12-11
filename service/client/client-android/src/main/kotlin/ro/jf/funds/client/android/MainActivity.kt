package ro.jf.funds.client.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import ro.jf.funds.client.android.ui.FundsApp
import ro.jf.funds.client.android.ui.theme.FundsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FundsTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FundsApp()
                }
            }
        }
    }
}
