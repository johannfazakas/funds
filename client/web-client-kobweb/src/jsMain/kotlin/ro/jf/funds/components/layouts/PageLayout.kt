package ro.jf.funds.components.layouts

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxSize
import com.varabyte.kobweb.compose.ui.modifiers.fillMaxWidth
import com.varabyte.kobweb.compose.ui.modifiers.padding
import org.jetbrains.compose.web.css.px
import ro.jf.funds.components.sections.Sidebar

@Composable
fun PageLayout(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxSize()) {
        Sidebar()
        Box(Modifier.fillMaxWidth().padding(24.px)) {
            content()
        }
    }
}
