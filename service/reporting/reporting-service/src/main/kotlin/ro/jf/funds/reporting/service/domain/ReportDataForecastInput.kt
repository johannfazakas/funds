package ro.jf.funds.reporting.service.domain

data class ReportDataForecastInput<T>(
    val interval: ReportDataInterval,
    val realData: ByBucket<T>,
    val forecastConfiguration: ForecastConfiguration,
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
                realInput.dataConfiguration.forecast,
                realInput.dataConfiguration.groups?.map { it.name } ?: emptyList()
            )
    }
}