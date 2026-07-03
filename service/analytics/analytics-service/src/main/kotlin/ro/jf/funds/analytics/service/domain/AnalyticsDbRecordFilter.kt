package ro.jf.funds.analytics.service.domain

import com.benasher44.uuid.Uuid
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.UnitType

data class AnalyticsDbRecordFilter(
    val fundIds: List<Uuid> = emptyList(),
    val units: List<FinancialUnit> = emptyList(),
    val transactionTypes: List<TransactionType> = emptyList(),
    val unitTypes: List<UnitType> = emptyList(),
)
