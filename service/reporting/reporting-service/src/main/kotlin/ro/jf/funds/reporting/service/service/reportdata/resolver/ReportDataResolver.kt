package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.reporting.service.domain.ByBucket
import ro.jf.funds.reporting.service.domain.ReportDataForecastInput
import ro.jf.funds.reporting.service.domain.ReportDataResolverInput

interface ReportDataResolver<T> {
    suspend fun resolve(input: ReportDataResolverInput): ByBucket<T>
    suspend fun forecast(input: ReportDataForecastInput<T>): ByBucket<T>
}