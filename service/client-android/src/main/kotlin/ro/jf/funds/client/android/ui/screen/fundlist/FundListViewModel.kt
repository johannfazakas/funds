package ro.jf.funds.client.android.ui.screen.fundlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.benasher44.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ro.jf.funds.client.sdk.FundClient
import ro.jf.funds.fund.api.model.FundTO

data class FundListState(
    val funds: List<FundTO> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class FundListViewModel(
    private val fundClient: FundClient = FundClient()
) : ViewModel() {
    private val log = Logger.withTag("FundListViewModel")

    private val _state = MutableStateFlow(FundListState())
    val state: StateFlow<FundListState> = _state.asStateFlow()

    fun loadFunds(userId: Uuid) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val funds = fundClient.listFunds(userId)
                _state.value = FundListState(
                    funds = funds,
                    isLoading = false
                )
                log.i { "Loaded ${funds.size} funds" }
            } catch (e: Exception) {
                log.e(e) { "Failed to load funds" }
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load funds: ${e.message}"
                )
            }
        }
    }
}
