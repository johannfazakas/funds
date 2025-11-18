package ro.jf.funds.client.android.ui.screen.fundlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benasher44.uuid.uuidFrom
import ro.jf.funds.client.api.model.FundTO

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundListScreen(
    viewModel: FundListViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    val userId = remember { uuidFrom("00000000-0000-0000-0000-000000000001") }

    LaunchedEffect(Unit) {
        viewModel.loadFunds(userId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Funds") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadFunds(userId) }) {
                            Text("Retry")
                        }
                    }
                }
                state.funds.isEmpty() -> {
                    Text(
                        text = "No funds available",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.funds) { fund ->
                            FundItem(fund = fund)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FundItem(fund: FundTO) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = fund.name.toString(),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ID: ${fund.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
