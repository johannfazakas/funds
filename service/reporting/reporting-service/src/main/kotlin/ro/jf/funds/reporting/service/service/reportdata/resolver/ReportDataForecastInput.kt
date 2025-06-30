package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.reporting.service.domain.ByBucket
import ro.jf.funds.reporting.service.domain.ForecastReportFeature
import ro.jf.funds.reporting.service.domain.ReportDataInterval

data class ReportDataForecastInput<T>(
    val interval: ReportDataInterval,
    val realData: ByBucket<T>,
    val forecastConfiguration: ForecastReportFeature,
    val groups: List<String>,
) {
    companion object {
        fun <T> from(
            realInput: ReportDataResolverInput,
            realData: ByBucket<T>,
        ): ReportDataForecastInput<T> =
            ReportDataForecastInput(
                realInput.interval,
                realData,
                realInput.dataConfiguration.features.forecast,
                realInput.dataConfiguration.groups?.map { it.name } ?: emptyList()
            )
    }
}