package ro.jf.funds.components.sections

import androidx.compose.runtime.Composable
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Spacer
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.layout.Surface
import com.varabyte.kobweb.silk.components.text.SpanText
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.vh
import ro.jf.funds.AppState

@Composable
fun Sidebar() {
    val ctx = rememberPageContext()

    Surface(
        Modifier
            .width(220.px)
            .minHeight(100.vh)
            .padding(16.px)
    ) {
        Column(Modifier.fillMaxHeight()) {
            SpanText(
                "Funds",
                Modifier
                    .fontSize(22.px)
                    .fontWeight(FontWeight.Bold)
                    .margin(bottom = 32.px)
            )

            SpanText(
                "DATA",
                Modifier
                    .fontSize(11.px)
                    .fontWeight(FontWeight.Bold)
                    .margin(bottom = 8.px)
                    .opacity(0.5)
            )

            SidebarLink("Funds", "/funds", ctx.route.path == "/funds") {
                ctx.router.navigateTo("/funds")
            }

            Spacer()

            SpanText(
                "Logout",
                Modifier
                    .fontSize(14.px)
                    .cursor(Cursor.Pointer)
                    .padding(8.px)
                    .onClick {
                        AppState.clearUserId()
                        ctx.router.navigateTo("/")
                    }
            )
        }
    }
}

@Composable
private fun SidebarLink(label: String, path: String, active: Boolean, onClick: () -> Unit) {
    SpanText(
        label,
        Modifier
            .fontSize(14.px)
            .padding(topBottom = 6.px, leftRight = 8.px)
            .borderRadius(4.px)
            .cursor(Cursor.Pointer)
            .fontWeight(if (active) FontWeight.Bold else FontWeight.Normal)
            .opacity(if (active) 1.0 else 0.7)
            .onClick { onClick() }
    )
}
