package ro.jf.funds.reporting.api.model

enum class ReportViewType {
    EXPENSE,
    ;

    companion object {
        fun fromString(value: String): ReportViewType = entries.single { it.name == value }
    }
}
