package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.reporting.service.domain.ByBucket

interface ReportDataResolver<T> {
    suspend fun resolve(input: ReportDataResolverInput): ByBucket<T>?
    suspend fun forecast(input: ReportDataForecastInput<T>): ByBucket<T>?
}
