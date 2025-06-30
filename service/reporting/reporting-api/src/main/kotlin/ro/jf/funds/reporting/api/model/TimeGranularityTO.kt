package ro.jf.funds.reporting.api.model

enum class TimeGranularityTO {
    DAILY,
    MONTHLY,
    YEARLY,
    ;

    companion object {
        fun fromString(value: String): TimeGranularityTO {
            return TimeGranularityTO.entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown TimeGranularity: $value")
        }
    }
}
