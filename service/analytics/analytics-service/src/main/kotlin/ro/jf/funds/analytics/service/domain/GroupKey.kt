package ro.jf.funds.analytics.service.domain

sealed interface GroupKey {
    val apiValue: String

    data object Ungrouped : GroupKey {
        override val apiValue: String = "UNGROUPED"
    }

    data class ByFund(val fundId: String) : GroupKey {
        override val apiValue: String = fundId
    }

    data class ByAccount(val accountId: String) : GroupKey {
        override val apiValue: String = accountId
    }

    data class ByFinancialUnit(val unit: String) : GroupKey {
        override val apiValue: String = unit
    }

    data class ByCategory(val category: String?) : GroupKey {
        override val apiValue: String = category ?: "UNCATEGORIZED"
    }
}
