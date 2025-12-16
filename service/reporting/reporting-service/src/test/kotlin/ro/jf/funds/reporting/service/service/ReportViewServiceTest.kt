package ro.jf.funds.reporting.service.service

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.whenever
import ro.jf.funds.platform.api.model.Currency.Companion.RON
import ro.jf.funds.platform.api.model.labelsOf
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import java.util.UUID.randomUUID

class ReportViewServiceTest {

    private val reportViewRepository = mock<ReportViewRepository>()

    private val reportViewService =
        ReportViewService(reportViewRepository)

    private val userId = randomUUID()
    private val reportViewId = randomUUID()
    private val reportViewName = "view name"
    private val expensesFundId = randomUUID()
    private val allLabels = labelsOf("need", "want")
    private val reportDataConfiguration = ReportDataConfiguration(
        currency = RON,
        groups = listOf(
            ReportGroup(
                name = "need",
                filter = RecordFilter(labels = labelsOf("need"))
            ),
            ReportGroup(
                name = "want",
                filter = RecordFilter(labels = labelsOf("want"))
            ),
        ),
        reports = ReportsConfiguration(
            net = NetReportConfiguration(enabled = true, filter = RecordFilter(labels = allLabels)),
            valueReport = ValueReportConfiguration(enabled = true),
        ),
    )
    private val reportViewCommand = CreateReportViewCommand(
        userId = userId,
        name = reportViewName,
        fundId = expensesFundId,
        dataConfiguration = reportDataConfiguration
    )

    @Test
    fun `create report view should create report view`(): Unit = runBlocking {
        whenever(reportViewRepository.findByName(userId, reportViewName)).thenReturn(null)
        whenever(reportViewRepository.save(reportViewCommand))
            .thenReturn(ReportView(reportViewId, userId, reportViewName, expensesFundId, reportDataConfiguration))

        val reportView = reportViewService.createReportView(userId, reportViewCommand)

        assertThat(reportView.id).isEqualTo(reportViewId)
        assertThat(reportView.userId).isEqualTo(userId)
        assertThat(reportView.name).isEqualTo(reportViewName)
        assertThat(reportView.fundId).isEqualTo(expensesFundId)

        verify(reportViewRepository, times(1))
            .save(reportViewCommand)
    }

    @Test
    fun `create report view with same name should raise error`(): Unit = runBlocking {
        whenever(reportViewRepository.findByName(userId, reportViewName))
            .thenReturn(
                ReportView(
                    reportViewId, userId, reportViewName, expensesFundId, reportDataConfiguration
                )
            )

        assertThatThrownBy { runBlocking { reportViewService.createReportView(userId, reportViewCommand) } }
            .isInstanceOf(ReportingException.ReportViewAlreadyExists::class.java)

        verify(reportViewRepository, never()).save(reportViewCommand)
    }

    @Test
    fun `create report view with grouped net feature without groups should raise error`(): Unit = runBlocking {
        val reportViewCommand = CreateReportViewCommand(
            userId = userId,
            name = reportViewName,
            fundId = expensesFundId,
            dataConfiguration = reportDataConfiguration.copy(
                groups = emptyList(),
                reports = ReportsConfiguration(
                    groupedNet = GenericReportConfiguration(enabled = true)
                )
            )
        )
        whenever(reportViewRepository.findByName(userId, reportViewName)).thenReturn(null)

        assertThatThrownBy { runBlocking { reportViewService.createReportView(userId, reportViewCommand) } }
            .isInstanceOf(ReportingException.MissingGroupsRequiredForFeature::class.java)

        verify(reportViewRepository, never()).save(reportViewCommand)
    }

    @Test
    fun `create report view with grouped budgets feature without groups should raise error`(): Unit = runBlocking {
        val reportViewCommand = CreateReportViewCommand(
            userId = userId,
            name = reportViewName,
            fundId = expensesFundId,
            dataConfiguration = reportDataConfiguration.copy(
                groups = emptyList(),
                reports = ReportsConfiguration(
                    groupedBudget = GroupedBudgetReportConfiguration(enabled = true, distributions = emptyList())
                )
            )
        )
        whenever(reportViewRepository.findByName(userId, reportViewName)).thenReturn(null)

        assertThatThrownBy { runBlocking { reportViewService.createReportView(userId, reportViewCommand) } }
            .isInstanceOf(ReportingException.MissingGroupsRequiredForFeature::class.java)
    }

    @Test
    fun `create report view with grouped budgets feature with empty distributions should raise error`(
    ): Unit = runBlocking {
        val reportViewCommand = CreateReportViewCommand(
            userId = userId,
            name = reportViewName,
            fundId = expensesFundId,
            dataConfiguration = reportDataConfiguration.copy(
                groups = listOf(
                    ReportGroup(
                        name = "need",
                        filter = RecordFilter(labels = labelsOf("need"))
                    ),
                    ReportGroup(
                        name = "want",
                        filter = RecordFilter(labels = labelsOf("want"))
                    ),
                ),
                reports = ReportsConfiguration(
                    groupedBudget = GroupedBudgetReportConfiguration(enabled = true, distributions = emptyList())
                )
            )
        )
        whenever(reportViewRepository.findByName(userId, reportViewName)).thenReturn(null)

        assertThatThrownBy { runBlocking { reportViewService.createReportView(userId, reportViewCommand) } }
            .isInstanceOf(ReportingException.MissingGroupBudgetDistributions::class.java)
    }

    @Test
    fun `create report view with grouped budgets feature without default distribution should raise error`(
    ): Unit = runBlocking {
        val reportViewCommand = CreateReportViewCommand(
            userId = userId,
            name = reportViewName,
            fundId = expensesFundId,
            dataConfiguration = reportDataConfiguration.copy(
                groups = listOf(
                    ReportGroup(
                        name = "need",
                        filter = RecordFilter(labels = labelsOf("need"))
                    ),
                    ReportGroup(
                        name = "want",
                        filter = RecordFilter(labels = labelsOf("want"))
                    ),
                ),
                reports = ReportsConfiguration(
                    groupedBudget = GroupedBudgetReportConfiguration(
                        enabled = true, distributions = listOf(
                            GroupedBudgetReportConfiguration.BudgetDistribution(
                                default = false,
                                from = YearMonth(2020, 2),
                                groups = listOf(
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("need", 50),
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("want", 50)
                                )
                            )
                        )
                    )
                )
            )
        )
        whenever(reportViewRepository.findByName(userId, reportViewName)).thenReturn(null)

        assertThatThrownBy { runBlocking { reportViewService.createReportView(userId, reportViewCommand) } }
            .isInstanceOf(ReportingException.NoUniqueGroupBudgetDefaultDistribution::class.java)
    }

    @Test
    fun `create report view with grouped budgets feature with multiple default distributions should raise error`(
    ): Unit = runBlocking {
        val reportViewCommand = CreateReportViewCommand(
            userId = userId,
            name = reportViewName,
            fundId = expensesFundId,
            dataConfiguration = reportDataConfiguration.copy(
                groups = listOf(
                    ReportGroup(
                        name = "need",
                        filter = RecordFilter(labels = labelsOf("need"))
                    ),
                    ReportGroup(
                        name = "want",
                        filter = RecordFilter(labels = labelsOf("want"))
                    ),
                ),
                reports = ReportsConfiguration(
                    groupedBudget = GroupedBudgetReportConfiguration(
                        enabled = true, distributions = listOf(
                            GroupedBudgetReportConfiguration.BudgetDistribution(
                                default = true,
                                from = YearMonth(2020, 2),
                                groups = listOf(
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("need", 50),
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("want", 50)
                                )
                            ),
                            GroupedBudgetReportConfiguration.BudgetDistribution(
                                default = true,
                                from = YearMonth(2020, 2),
                                groups = listOf(
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("need", 50),
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("want", 50)
                                )
                            )
                        )
                    )
                )
            )
        )
        whenever(reportViewRepository.findByName(userId, reportViewName)).thenReturn(null)

        assertThatThrownBy { runBlocking { reportViewService.createReportView(userId, reportViewCommand) } }
            .isInstanceOf(ReportingException.NoUniqueGroupBudgetDefaultDistribution::class.java)
    }

    @Test
    fun `create report view with grouped budgets feature with non default distribution without from year month should raise error`(
    ): Unit = runBlocking {
        val reportViewCommand = CreateReportViewCommand(
            userId = userId,
            name = reportViewName,
            fundId = expensesFundId,
            dataConfiguration = reportDataConfiguration.copy(
                groups = listOf(
                    ReportGroup(
                        name = "need",
                        filter = RecordFilter(labels = labelsOf("need"))
                    ),
                    ReportGroup(
                        name = "want",
                        filter = RecordFilter(labels = labelsOf("want"))
                    ),
                ),
                reports = ReportsConfiguration(
                    groupedBudget = GroupedBudgetReportConfiguration(
                        enabled = true, distributions = listOf(
                            GroupedBudgetReportConfiguration.BudgetDistribution(
                                default = true,
                                from = YearMonth(2020, 2),
                                groups = listOf(
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("need", 50),
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("want", 50)
                                )
                            ),
                            GroupedBudgetReportConfiguration.BudgetDistribution(
                                default = false,
                                from = null,
                                groups = listOf(
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("need", 50),
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("want", 50)
                                )
                            )
                        )
                    )
                )
            )
        )
        whenever(reportViewRepository.findByName(userId, reportViewName)).thenReturn(null)

        assertThatThrownBy { runBlocking { reportViewService.createReportView(userId, reportViewCommand) } }
            .isInstanceOf(ReportingException.MissingStartYearMonthOnGroupBudgetDistribution::class.java)
    }

    @Test
    fun `create report view with grouped budgets feature with multiple distributions with same from date should raise error`(
    ): Unit = runBlocking {
        val reportViewCommand = CreateReportViewCommand(
            userId = userId,
            name = reportViewName,
            fundId = expensesFundId,
            dataConfiguration = reportDataConfiguration.copy(
                groups = listOf(
                    ReportGroup(
                        name = "need",
                        filter = RecordFilter(labels = labelsOf("need"))
                    ),
                    ReportGroup(
                        name = "want",
                        filter = RecordFilter(labels = labelsOf("want"))
                    ),
                ),
                reports = ReportsConfiguration(
                    groupedBudget = GroupedBudgetReportConfiguration(
                        enabled = true, distributions = listOf(
                            GroupedBudgetReportConfiguration.BudgetDistribution(
                                default = true,
                                from = null,
                                groups = listOf(
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("need", 50),
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("want", 50)
                                )
                            ),
                            GroupedBudgetReportConfiguration.BudgetDistribution(
                                default = false,
                                from = YearMonth(2020, 2),
                                groups = listOf(
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("need", 50),
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("want", 50)
                                )
                            ),
                            GroupedBudgetReportConfiguration.BudgetDistribution(
                                default = false,
                                from = YearMonth(2020, 2),
                                groups = listOf(
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("need", 60),
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("want", 40)
                                )
                            )
                        )
                    )
                )
            )
        )
        whenever(reportViewRepository.findByName(userId, reportViewName)).thenReturn(null)

        assertThatThrownBy { runBlocking { reportViewService.createReportView(userId, reportViewCommand) } }
            .isInstanceOf(ReportingException.ConflictingStartYearMonthOnGroupBudgetDistribution::class.java)
    }

    @Test
    fun `create report view with grouped budgets feature with distribution not covering all groups should raise error`(
    ): Unit = runBlocking {
        val reportViewCommand = CreateReportViewCommand(
            userId = userId,
            name = reportViewName,
            fundId = expensesFundId,
            dataConfiguration = reportDataConfiguration.copy(
                groups = listOf(
                    ReportGroup(
                        name = "need",
                        filter = RecordFilter(labels = labelsOf("need"))
                    ),
                    ReportGroup(
                        name = "want",
                        filter = RecordFilter(labels = labelsOf("want"))
                    ),
                ),
                reports = ReportsConfiguration(
                    groupedBudget = GroupedBudgetReportConfiguration(
                        enabled = true, distributions = listOf(
                            GroupedBudgetReportConfiguration.BudgetDistribution(
                                default = true,
                                from = null,
                                groups = listOf(
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("need", 50),
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("want", 50)
                                )
                            ),
                            GroupedBudgetReportConfiguration.BudgetDistribution(
                                default = false,
                                from = YearMonth(2020, 2),
                                groups = listOf(
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("need", 50)
                                )
                            )
                        )
                    )
                )
            )
        )
        whenever(reportViewRepository.findByName(userId, reportViewName)).thenReturn(null)

        assertThatThrownBy { runBlocking { reportViewService.createReportView(userId, reportViewCommand) } }
            .isInstanceOf(ReportingException.GroupBudgetDistributionGroupsDoNotMatch::class.java)
    }

    @Test
    fun `create report view with grouped budgets feature with distribution covering not existing groups should raise error`(
    ): Unit = runBlocking {
        val reportViewCommand = CreateReportViewCommand(
            userId = userId,
            name = reportViewName,
            fundId = expensesFundId,
            dataConfiguration = reportDataConfiguration.copy(
                groups = listOf(
                    ReportGroup(
                        name = "need",
                        filter = RecordFilter(labels = labelsOf("need"))
                    ),
                    ReportGroup(
                        name = "want",
                        filter = RecordFilter(labels = labelsOf("want"))
                    ),
                ),
                reports = ReportsConfiguration(
                    groupedBudget = GroupedBudgetReportConfiguration(
                        enabled = true, distributions = listOf(
                            GroupedBudgetReportConfiguration.BudgetDistribution(
                                default = true,
                                from = null,
                                groups = listOf(
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("need", 50),
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("want", 50)
                                )
                            ),
                            GroupedBudgetReportConfiguration.BudgetDistribution(
                                default = false,
                                from = YearMonth(2020, 2),
                                groups = listOf(
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("need", 50),
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("want", 50),
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("other", 50)
                                )
                            )
                        )
                    )
                )
            )
        )
        whenever(reportViewRepository.findByName(userId, reportViewName)).thenReturn(null)

        assertThatThrownBy { runBlocking { reportViewService.createReportView(userId, reportViewCommand) } }
            .isInstanceOf(ReportingException.GroupBudgetDistributionGroupsDoNotMatch::class.java)
    }

    @Test
    fun `create report view with grouped budgets feature with distribution percentage sum not equal to 100 should raise error`(
    ): Unit = runBlocking {
        val reportViewCommand = CreateReportViewCommand(
            userId = userId,
            name = reportViewName,
            fundId = expensesFundId,
            dataConfiguration = reportDataConfiguration.copy(
                groups = listOf(
                    ReportGroup(
                        name = "need",
                        filter = RecordFilter(labels = labelsOf("need"))
                    ),
                    ReportGroup(
                        name = "want",
                        filter = RecordFilter(labels = labelsOf("want"))
                    ),
                ),
                reports = ReportsConfiguration(
                    groupedBudget = GroupedBudgetReportConfiguration(
                        enabled = true, distributions = listOf(
                            GroupedBudgetReportConfiguration.BudgetDistribution(
                                default = true,
                                from = null,
                                groups = listOf(
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("need", 50),
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("want", 50)
                                )
                            ),
                            GroupedBudgetReportConfiguration.BudgetDistribution(
                                default = false,
                                from = YearMonth(2020, 2),
                                groups = listOf(
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("need", 50),
                                    GroupedBudgetReportConfiguration.GroupBudgetPercentage("want", 40)
                                )
                            )
                        )
                    )
                )
            )
        )
        whenever(reportViewRepository.findByName(userId, reportViewName)).thenReturn(null)

        assertThatThrownBy { runBlocking { reportViewService.createReportView(userId, reportViewCommand) } }
            .isInstanceOf(ReportingException.GroupBudgetPercentageSumInvalid::class.java)
    }
}
