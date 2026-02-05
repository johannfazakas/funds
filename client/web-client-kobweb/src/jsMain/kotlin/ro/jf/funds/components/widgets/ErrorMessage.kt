package ro.jf.funds.components.widgets

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.gap
import com.varabyte.kobweb.compose.ui.modifiers.padding
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.css.px

@Composable
fun ErrorMessage(message: String, onRetry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(24.px).gap(8.px),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SpanText(message)
        if (onRetry != null) {
            Button(onClick = { onRetry() }) {
                SpanText("Retry")
            }
        }
    }
}
