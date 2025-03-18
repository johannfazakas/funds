package ro.jf.funds.reporting.service.service.reportdata.resolver

import ro.jf.funds.reporting.api.model.DateInterval

fun interface ReportDataResolver<T> {
    fun resolve(
        input: ReportDataResolverInput,
    ): Map<DateInterval, T>?
}
