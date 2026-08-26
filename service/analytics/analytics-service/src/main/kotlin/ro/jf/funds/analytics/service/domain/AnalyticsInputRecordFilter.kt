package ro.jf.funds.analytics.service.domain

import com.benasher44.uuid.Uuid
import ro.jf.funds.fund.api.model.TransactionType
import ro.jf.funds.platform.api.model.FinancialUnit
import ro.jf.funds.platform.api.model.UnitType

data class AnalyticsInputRecordFilter(
    val fundIds: Set<Uuid> = emptySet(),
    val units: Set<FinancialUnit> = emptySet(),
) {
    fun toDbFilter(
        transactionTypes: List<TransactionType> = emptyList(),
        unitTypes: List<UnitType> = emptyList(),
    ) = AnalyticsDbRecordFilter(
        fundIds = fundIds.toList(),
        units = units.toList(),
        transactionTypes = transactionTypes,
        unitTypes = unitTypes,
    )
}
