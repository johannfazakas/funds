package ro.jf.funds.reporting.api.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonClassDiscriminator
import ro.jf.funds.reporting.api.serializer.YearMonthSerializer

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("granularity")
sealed class ReportDataIntervalTO(
) {
    abstract val granularity: TimeGranularityTO
    abstract val fromDate: LocalDate
    abstract val toDate: LocalDate
    abstract val forecastUntilDate: LocalDate?

    @Serializable()
    @SerialName("DAILY")
    data class Daily(
        override val fromDate: LocalDate,
        override val toDate: LocalDate,
        override val forecastUntilDate: LocalDate? = null,
    ) : ReportDataIntervalTO() {
        @Transient
        override val granularity = TimeGranularityTO.DAILY
    }

    @Serializable()
    @SerialName("MONTHLY")
    data class Monthly(
        @Serializable(with = YearMonthSerializer::class)
        val fromYearMonth: YearMonthTO,
        @Serializable(with = YearMonthSerializer::class)
        val toYearMonth: YearMonthTO,
        @Serializable(with = YearMonthSerializer::class)
        val forecastUntilYearMonth: YearMonthTO? = null,
    ) : ReportDataIntervalTO() {
        @Transient
        override val granularity = TimeGranularityTO.MONTHLY
        override val fromDate: LocalDate = fromYearMonth.startDate
        override val toDate: LocalDate = toYearMonth.endDate
        override val forecastUntilDate: LocalDate? = forecastUntilYearMonth?.endDate
    }

    @Serializable()
    @SerialName("YEARLY")
    data class Yearly(
        val fromYear: Int,
        val toYear: Int,
        val forecastUntilYear: Int? = null,
    ) : ReportDataIntervalTO() {
        @Transient
        override val granularity = TimeGranularityTO.YEARLY
        override val toDate: LocalDate = LocalDate(toYear, 12, 31)
        override val fromDate: LocalDate = LocalDate(fromYear, 1, 1)
        override val forecastUntilDate: LocalDate? = forecastUntilYear?.let { LocalDate(it, 12, 31) }
    }
}

@Serializable
data class DateIntervalTO(
    val from: LocalDate,
    val to: LocalDate,
) {
    init {
        require(from <= to) { "From date must be before or equal to the to date. from = $from, to = $to." }
    }

    constructor(from: YearMonthTO, to: YearMonthTO) : this(
        LocalDate(from.year, from.month, 1),
        LocalDate(to.year, to.month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY),
    )
}

@Serializable
data class YearMonthTO(
    val year: Int,
    val month: Int,
) : Comparable<YearMonthTO> {
    companion object {
        fun parse(serialized: String): YearMonthTO {
            val (year, month) = serialized.split("-").map { it.toInt() }
            return YearMonthTO(year, month)
        }
    }

    override fun compareTo(other: YearMonthTO): Int =
        LocalDate(year, month, 1).compareTo(LocalDate(other.year, other.month, 1))

    val startDate by lazy { LocalDate(year, month, 1) }
    val endDate by lazy { LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY) }

    fun next(): YearMonthTO {
        val nextMonth = if (month == 12) 1 else month + 1
        val nextYear = if (nextMonth == 1) year + 1 else year
        return YearMonthTO(nextYear, nextMonth)
    }

    override fun toString(): String {
        val monthValue = if (this.month < 10) "0${this.month}" else this.month.toString()
        return "${this.year}-$monthValue"
    }
}
