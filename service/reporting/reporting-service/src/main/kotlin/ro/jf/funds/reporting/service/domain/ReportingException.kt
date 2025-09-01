package ro.jf.funds.reporting.service.domain

import java.util.*


sealed class ReportingException(message: String) : RuntimeException(message) {
    class MissingGranularity : ReportingException("Missing granularity")
    class MissingIntervalStart : ReportingException("Missing interval start")
    class MissingIntervalEnd : ReportingException("Missing interval end")

    class ReportViewNotFound(val userId: UUID, val reportViewId: UUID) : ReportingException("Report view not found")
    class ReportViewAlreadyExists(val userId: UUID, val reportViewName: String) :
        ReportingException("Report view already exists")

    class ReportRecordConversionRateNotFound(val recordId: UUID) :
        ReportingException("Report record conversion rate not found")

    class MissingGroupsRequiredForFeature(val userId: UUID, val featureName: String) :
        ReportingException("Missing groups required for feature")

    class MissingGroupBudgetDistributions(val userId: UUID) :
        ReportingException("Missing group budget distributions")

    class NoUniqueGroupBudgetDefaultDistribution(val userId: UUID) :
        ReportingException("No unique group budget default distribution")

    class MissingStartYearMonthOnGroupBudgetDistribution(val userId: UUID) :
        ReportingException("Missing start year month on group budget distribution")

    class ConflictingStartYearMonthOnGroupBudgetDistribution(val userId: UUID) :
        ReportingException("Conflicting start year month on group budget distribution")

    class GroupBudgetDistributionGroupsDoNotMatch(val userId: UUID) :
        ReportingException("Group budget distribution groups do not match")

    class GroupBudgetPercentageSumInvalid(val userId: UUID, val from: YearMonth?, val sum: Int) :
        ReportingException("Group budget percentage sum $sum is invalid for distribution starting at $from")

    class FeatureDisabled(val userId: UUID, reportViewId: UUID) :
        ReportingException("Feature is disabled for user $userId on reportView $reportViewId")
}
