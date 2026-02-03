package ro.jf.funds.pages

import androidx.compose.runtime.*
import com.varabyte.kobweb.compose.css.Cursor
import com.varabyte.kobweb.compose.css.FontWeight
import com.varabyte.kobweb.compose.foundation.layout.Box
import com.varabyte.kobweb.compose.foundation.layout.Column
import com.varabyte.kobweb.compose.foundation.layout.Row
import com.varabyte.kobweb.compose.foundation.layout.Spacer
import com.varabyte.kobweb.compose.ui.Alignment
import com.varabyte.kobweb.compose.ui.Modifier
import com.varabyte.kobweb.compose.ui.graphics.Colors
import com.varabyte.kobweb.compose.ui.modifiers.*
import com.varabyte.kobweb.core.Page
import com.varabyte.kobweb.core.rememberPageContext
import com.varabyte.kobweb.silk.components.forms.Button
import com.varabyte.kobweb.silk.components.forms.TextInput
import com.varabyte.kobweb.silk.components.layout.Surface
import com.varabyte.kobweb.silk.components.overlay.Overlay
import com.varabyte.kobweb.silk.components.text.SpanText
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import ro.jf.funds.AppState
import ro.jf.funds.components.layouts.PageLayout
import ro.jf.funds.components.widgets.ErrorMessage
import ro.jf.funds.components.widgets.LoadingSpinner
import ro.jf.funds.fund.api.model.FundTO

@Page
@Composable
fun FundsPage() {
    val ctx = rememberPageContext()
    val scope = rememberCoroutineScope()
    val userId = AppState.getUserId()

    LaunchedEffect(Unit) {
        if (userId == null) {
            ctx.router.navigateTo("/")
            return@LaunchedEffect
        }
    }

    if (userId == null) return

    var funds by remember { mutableStateOf<List<FundTO>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newFundName by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }

    var fundToDelete by remember { mutableStateOf<FundTO?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    fun loadFunds() {
        loading = true
        error = null
        scope.launch {
            try {
                funds = AppState.fundClient.listFunds(userId)
            } catch (e: Exception) {
                error = "Failed to load funds: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(userId) {
        loadFunds()
    }

    PageLayout {
        Column(Modifier.fillMaxWidth().gap(16.px)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpanText("Funds", Modifier.fontSize(24.px).fontWeight(FontWeight.Bold))
                Spacer()
                Button(onClick = {
                    newFundName = ""
                    createError = null
                    showCreateDialog = true
                }) {
                    SpanText("Create Fund")
                }
            }

            when {
                loading -> LoadingSpinner()
                error != null -> ErrorMessage(error!!) { loadFunds() }
                funds.isEmpty() -> Box(Modifier.padding(24.px)) {
                    SpanText("No funds yet — create one to get started.")
                }
                else -> FundTable(funds, onDelete = { fund ->
                    deleteError = null
                    fundToDelete = fund
                })
            }
        }
    }

    if (showCreateDialog) {
        Overlay(Modifier.onClick { if (!creating) showCreateDialog = false }) {
            ModalDialog {
                Column(Modifier.gap(12.px).width(300.px)) {
                    SpanText("Create Fund", Modifier.fontSize(18.px).fontWeight(FontWeight.Bold))

                    TextInput(
                        text = newFundName,
                        onTextChange = { newFundName = it; createError = null },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = "Enter fund name",
                        enabled = !creating,
                    )

                    createError?.let { msg ->
                        SpanText(msg, Modifier.color(Colors.Red))
                    }

                    Row(Modifier.fillMaxWidth().gap(8.px)) {
                        Spacer()
                        Button(
                            onClick = { showCreateDialog = false },
                            enabled = !creating,
                        ) {
                            SpanText("Cancel")
                        }
                        Button(
                            onClick = {
                                if (newFundName.isBlank()) {
                                    createError = "Fund name cannot be empty"
                                    return@Button
                                }
                                creating = true
                                createError = null
                                scope.launch {
                                    try {
                                        AppState.fundClient.createFund(userId, newFundName.trim())
                                        showCreateDialog = false
                                        loadFunds()
                                    } catch (e: Exception) {
                                        createError = "Failed to create fund: ${e.message}"
                                    } finally {
                                        creating = false
                                    }
                                }
                            },
                            enabled = !creating,
                        ) {
                            SpanText(if (creating) "Creating..." else "Create")
                        }
                    }
                }
            }
        }
    }

    fundToDelete?.let { fund ->
        Overlay(Modifier.onClick { if (!deleting) fundToDelete = null }) {
            ModalDialog {
                Column(Modifier.gap(12.px).width(300.px)) {
                    SpanText("Delete Fund", Modifier.fontSize(18.px).fontWeight(FontWeight.Bold))
                    SpanText("Are you sure you want to delete \"${fund.name}\"?")

                    deleteError?.let { msg ->
                        SpanText(msg, Modifier.color(Colors.Red))
                    }

                    Row(Modifier.fillMaxWidth().gap(8.px)) {
                        Spacer()
                        Button(
                            onClick = { fundToDelete = null },
                            enabled = !deleting,
                        ) {
                            SpanText("Cancel")
                        }
                        Button(
                            onClick = {
                                deleting = true
                                deleteError = null
                                scope.launch {
                                    try {
                                        AppState.fundClient.deleteFund(userId, fund.id)
                                        fundToDelete = null
                                        loadFunds()
                                    } catch (e: Exception) {
                                        deleteError = "Failed to delete fund: ${e.message}"
                                    } finally {
                                        deleting = false
                                    }
                                }
                            },
                            enabled = !deleting,
                        ) {
                            SpanText(if (deleting) "Deleting..." else "Delete")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FundTable(funds: List<FundTO>, onDelete: (FundTO) -> Unit) {
    Column(Modifier.fillMaxWidth().gap(1.px)) {
        Row(
            Modifier.fillMaxWidth().padding(topBottom = 8.px, leftRight = 12.px),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpanText("Name", Modifier.fontWeight(FontWeight.Bold).width(80.percent))
            Spacer()
        }

        funds.forEach { fund ->
            Surface(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(topBottom = 8.px, leftRight = 12.px),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpanText(fund.name.toString(), Modifier.width(80.percent))
                    Spacer()
                    SpanText(
                        "Delete",
                        Modifier
                            .fontSize(13.px)
                            .cursor(Cursor.Pointer)
                            .color(Colors.Red)
                            .onClick { onDelete(fund) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModalDialog(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier
                .padding(24.px)
                .borderRadius(8.px)
                .onClick { it.stopPropagation() }
        ) {
            content()
        }
    }
}
