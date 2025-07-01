package ro.jf.funds.reporting.service.service

import mu.KotlinLogging.logger
import ro.jf.funds.reporting.service.domain.CreateReportViewCommand
import ro.jf.funds.reporting.service.domain.ReportDataConfiguration
import ro.jf.funds.reporting.service.domain.ReportView
import ro.jf.funds.reporting.service.domain.ReportingException
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.utils.withSuspendingSpan
import java.util.*

private val log = logger { }

class ReportViewService(
    private val reportViewRepository: ReportViewRepository,
) {
    suspend fun createReportView(userId: UUID, payload: CreateReportViewCommand): ReportView = withSuspendingSpan {
        log.info { "Create report view for user $userId: $payload" }

        reportViewRepository.findByName(userId, payload.name)?.let {
            throw ReportingException.ReportViewAlreadyExists(userId, payload.name)
        }
        validateCreateReportViewRequest(payload, userId)
        val reportView = reportViewRepository.save(payload)

        reportView
    }

    suspend fun getReportView(userId: UUID, reportViewId: UUID): ReportView = withSuspendingSpan {
        reportViewRepository.findById(userId, reportViewId)
            ?: throw ReportingException.ReportViewNotFound(userId, reportViewId)
    }


    suspend fun deleteReportView(userId: UUID, reportViewId: UUID) = withSuspendingSpan {
        reportViewRepository.delete(userId, reportViewId)
    }

    suspend fun listReportViews(userId: UUID): List<ReportView> = withSuspendingSpan {
        reportViewRepository.findAll(userId)
    }

    private fun validateCreateReportViewRequest(payload: CreateReportViewCommand, userId: UUID) {
        val dataConfiguration = payload.dataConfiguration
        validateGroupedNetFeature(dataConfiguration, userId)
        validateGroupedBudgetFeature(dataConfiguration, userId)
    }

    private fun validateGroupedNetFeature(dataConfiguration: ReportDataConfiguration, userId: UUID) {
        if (dataConfiguration.reports.groupedNet.enabled && dataConfiguration.groups.isNullOrEmpty()) {
            throw ReportingException.MissingGroupsRequiredForFeature(userId, "groupedNet")
        }
    }

    private fun validateGroupedBudgetFeature(dataConfiguration: ReportDataConfiguration, userId: UUID) {
        val groupedBudget = dataConfiguration.reports.groupedBudget
        if (!groupedBudget.enabled) {
            return
        }
        if (dataConfiguration.groups.isNullOrEmpty()) {
            throw ReportingException.MissingGroupsRequiredForFeature(userId, "groupedBudget")
        }
        if (groupedBudget.distributions.isEmpty()) {
            throw ReportingException.MissingGroupBudgetDistributions(userId)
        }
        if (groupedBudget.distributions.count { it.default } != 1) {
            throw ReportingException.NoUniqueGroupBudgetDefaultDistribution(userId)
        }
        if (groupedBudget.distributions.any { !it.default && it.from == null }) {
            throw ReportingException.MissingStartYearMonthOnGroupBudgetDistribution(userId)
        }
        if (groupedBudget.distributions.mapNotNull { it.from }.groupBy { it }.any { it.value.size > 1 }) {
            throw ReportingException.ConflictingStartYearMonthOnGroupBudgetDistribution(userId)
        }
        val groupNames = dataConfiguration.groups.map { it.name }.sorted()
        if (groupedBudget.distributions.any { it.groups.map { group -> group.group }.sorted() != groupNames }) {
            throw ReportingException.GroupBudgetDistributionGroupsDoNotMatch(userId)
        }
        groupedBudget.distributions
            .map { it to it.groups.sumOf { group -> group.percentage } }
            .firstOrNull { (_, sum) -> sum != 100 }
            ?.let { (distribution, sum) ->
                throw ReportingException.GroupBudgetPercentageSumInvalid(userId, distribution.from, sum)
            }
    }
}
