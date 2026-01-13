package ro.jf.funds.client.android.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.benasher44.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ro.jf.funds.client.sdk.FundClient
import ro.jf.funds.fund.api.model.FundTO

data class FundListUiState(
    val funds: List<FundTO> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class FundListViewModel : ViewModel() {
    private val fundClient = FundClient(baseUrl = "http://10.0.2.2:5253")

    private val _uiState = MutableStateFlow(FundListUiState())
    val uiState: StateFlow<FundListUiState> = _uiState.asStateFlow()

    fun loadFunds(userId: Uuid) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val funds = fundClient.listFunds(userId)
                _uiState.value = FundListUiState(funds = funds, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = FundListUiState(isLoading = false, error = "Failed to load funds: ${e.message}")
            }
        }
    }
}

@Composable
fun FundListScreen(
    userId: Uuid,
    viewModel: FundListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(userId) {
        viewModel.loadFunds(userId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            uiState.error != null -> {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
            uiState.funds.isEmpty() -> {
                Text(
                    text = "No funds found",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.funds) { fund ->
                        FundItem(fund = fund)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FundItem(fund: FundTO) {
    Text(
        text = fund.name.value,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
}
