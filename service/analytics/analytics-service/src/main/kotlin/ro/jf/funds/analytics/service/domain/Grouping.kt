package ro.jf.funds.analytics.service.domain

import ro.jf.funds.analytics.api.model.GroupingCriteria

fun AnalyticsRecord.toGroupKey(groupBy: GroupingCriteria?): GroupKey = when (groupBy) {
    GroupingCriteria.FINANCIAL_UNIT -> GroupKey.ByFinancialUnit(unit.value)
    GroupingCriteria.ACCOUNT -> GroupKey.ByAccount(accountId.toString())
    GroupingCriteria.FUND -> GroupKey.ByFund(fundId.toString())
    GroupingCriteria.CATEGORY -> GroupKey.ByCategory(category?.value)
    null -> GroupKey.Ungrouped
}

fun InvestmentPosition.toGroupKey(groupBy: GroupingCriteria?): GroupKey = when (groupBy) {
    GroupingCriteria.FINANCIAL_UNIT -> GroupKey.ByFinancialUnit(instrumentUnit.value)
    GroupingCriteria.FUND -> GroupKey.ByFund(fundId.toString())
    GroupingCriteria.ACCOUNT -> GroupKey.ByAccount(accountId.toString())
    GroupingCriteria.CATEGORY -> GroupKey.ByCategory(category)
    null -> GroupKey.Ungrouped
}
