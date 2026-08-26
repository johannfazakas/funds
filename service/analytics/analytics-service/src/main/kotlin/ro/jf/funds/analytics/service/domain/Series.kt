package ro.jf.funds.analytics.service.domain

import ro.jf.funds.analytics.api.model.MetricTO

sealed class Series<out T : SeriesSlice> {

    sealed class Metric(val api: MetricTO) : Series<SeriesSlice.Scalars>()

    sealed class Internal<out T : SeriesSlice> : Series<T>()

    data object Balance : Metric(MetricTO.BALANCE)
    data object NetChange : Metric(MetricTO.NET_CHANGE)
    data object TotalInvestment : Metric(MetricTO.TOTAL_INVESTMENT)
    data object CurrentInvestment : Metric(MetricTO.CURRENT_INVESTMENT)
    data object TotalInstrumentValue : Metric(MetricTO.TOTAL_INSTRUMENT_VALUE)
    data object CurrencyValue : Metric(MetricTO.CURRENCY_VALUE)
    data object TotalProfit : Metric(MetricTO.TOTAL_PROFIT)
    data object CurrentProfit : Metric(MetricTO.CURRENT_PROFIT)
    data object TotalInterestRate : Metric(MetricTO.TOTAL_INTEREST_RATE)
    data object CurrentInterestRate : Metric(MetricTO.CURRENT_INTEREST_RATE)

    data object TransactionAmounts : Internal<SeriesSlice.Amounts>()
    data object OpenPositionRecords : Internal<SeriesSlice.Records>()
    data object InstrumentHoldings : Internal<SeriesSlice.Amounts>()
    data object CurrencyAmounts : Internal<SeriesSlice.Amounts>()
    data object PairedPositions : Internal<SeriesSlice.Positions>()

    companion object {
        // lazy so the list is not built during the sealed hierarchy's class initialization,
        // where a first touch through a data object would read its not-yet-assigned instance as null
        val entries: List<Series<*>> by lazy {
            listOf(
                Balance, NetChange, TotalInvestment, CurrentInvestment, TotalInstrumentValue,
                CurrencyValue, TotalProfit, CurrentProfit, TotalInterestRate, CurrentInterestRate,
                TransactionAmounts, OpenPositionRecords, InstrumentHoldings, CurrencyAmounts, PairedPositions,
            )
        }

        private val byApi: Map<MetricTO, Metric> by lazy {
            entries.filterIsInstance<Metric>().associateBy { it.api }
        }

        fun of(api: MetricTO): Metric = byApi.getValue(api)
    }
}
