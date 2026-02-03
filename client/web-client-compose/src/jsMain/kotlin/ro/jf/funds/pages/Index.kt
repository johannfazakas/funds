package ro.jf.funds.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.forms.TextInput
import com.varabyte.kobweb.silk.components.text.SpanText
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.vh
import ro.jf.funds.AppState

@Page
@Composable
fun LoginPage() {
    val ctx = rememberPageContext()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (AppState.isLoggedIn()) {
            ctx.router.navigateTo("/funds")
        }
    }

    var username by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        Modifier.fillMaxWidth().minHeight(100.vh),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(32.px).gap(16.px).width(320.px),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SpanText(
                "Funds",
                Modifier.fontSize(28.px).fontWeight(FontWeight.Bold).margin(bottom = 16.px),
            )

            TextInput(
                text = username,
                onTextChange = { username = it; error = null },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Enter your username",
                enabled = !loading,
            )

            error?.let { msg ->
                SpanText(msg, Modifier.color(Colors.Red))
            }

            Button(
                onClick = {
                    if (username.isBlank()) {
                        error = "Username cannot be empty"
                        return@Button
                    }
                    loading = true
                    error = null
                    scope.launch {
                        try {
                            val user = AppState.authenticationClient.loginWithUsername(username)
                            if (user != null) {
                                AppState.setUserId(user.id)
                                ctx.router.navigateTo("/funds")
                            } else {
                                error = "User not found"
                            }
                        } catch (e: Exception) {
                            error = "Login failed: ${e.message}"
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading,
            ) {
                SpanText(if (loading) "Logging in..." else "Login")
            }
        }
    }
}
