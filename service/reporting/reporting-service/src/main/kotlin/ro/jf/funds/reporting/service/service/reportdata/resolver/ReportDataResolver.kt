package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.reporting.service.domain.ByBucket

fun interface ReportDataResolver<T> {
    fun resolve(input: ReportDataResolverInput): ByBucket<T>?
}
