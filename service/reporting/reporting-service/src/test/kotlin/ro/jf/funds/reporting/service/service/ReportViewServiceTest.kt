package ro.jf.funds.reporting.service.service

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import ro.jf.funds.commons.model.Currency.Companion.EUR
import ro.jf.funds.commons.model.Currency.Companion.RON
import ro.jf.funds.commons.model.Label
import ro.jf.funds.commons.model.ListTO
import ro.jf.funds.commons.model.labelsOf
import ro.jf.funds.fund.sdk.FundTransactionSdk
import ro.jf.funds.historicalpricing.api.model.ConversionRequest
import ro.jf.funds.historicalpricing.api.model.ConversionResponse
import ro.jf.funds.historicalpricing.api.model.ConversionsRequest
import ro.jf.funds.historicalpricing.api.model.ConversionsResponse
import ro.jf.funds.historicalpricing.sdk.HistoricalPricingSdk
import ro.jf.funds.reporting.api.model.YearMonth
import ro.jf.funds.reporting.service.domain.*
import ro.jf.funds.reporting.service.persistence.ReportRecordRepository
import ro.jf.funds.reporting.service.persistence.ReportViewRepository
import ro.jf.funds.reporting.service.utils.record
import ro.jf.funds.reporting.service.utils.transaction
import java.math.BigDecimal
import java.util.UUID.randomUUID

class ReportViewServiceTest {

    private val reportViewRepository = mock<ReportViewRepository>()
    private val reportRecordRepository = mock<ReportRecordRepository>()
    private val fundTransactionSdk = mock<FundTransactionSdk>()
    private val historicalPricingSdk = mock<HistoricalPricingSdk>()

    private val reportViewService =
        ReportViewService(reportViewRepository, reportRecordRepository, fundTransactionSdk, historicalPricingSdk)

    private val userId = randomUUID()
    private val reportViewId = randomUUID()
    private val reportViewName = "view name"
    private val expensesFundId = randomUUID()
    private val bankAccountId = randomUUID()
    private val cashAccountId = randomUUID()
    private val dateTime1 = LocalDateTime.parse("2021-09-01T12:00:00")
    private val dateTime2 = LocalDateTime.parse("2021-09-03T12:00:00")
    private val allLabels = labelsOf("need", "want")
    private val reportDataConfiguration = ReportDataConfiguration(
        currency = RON,
        filter = RecordFilter(labels = allLabels),
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
        features = ReportDataFeaturesConfiguration(
            net = NetReportFeature(enabled = true, applyFilter = true),
            valueReport = GenericReportFeature(enabled = true),
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
        whenever(fundTransactionSdk.listTransactions(userId, expensesFundId)).thenReturn(ListTO.of())

        val reportView = reportViewService.createReportView(userId, reportViewCommand)

        assertThat(reportView.id).isEqualTo(reportViewId)
        assertThat(reportView.userId).isEqualTo(userId)
        assertThat(reportView.name).isEqualTo(reportViewName)
        assertThat(reportView.fundId).isEqualTo(expensesFundId)

        verify(reportViewRepository, times(1))
            .save(reportViewCommand)
    }

    @Test
    fun `create report view should store single fund report records`(): Unit = runBlocking {
        whenever(reportViewRepository.findByName(userId, reportViewName)).thenReturn(null)
        whenever(reportViewRepository.save(reportViewCommand))
            .thenReturn(
                ReportView(
                    reportViewId, userId, reportViewName, expensesFundId, reportDataConfiguration
                )
            )

        val transaction1 =
            transaction(
                userId, dateTime1, listOf(
                    record(expensesFundId, bankAccountId, BigDecimal("100.0"), RON, labelsOf("need"))
                )
            )
        val transaction2 =
            transaction(
                userId, dateTime2, listOf(
                    record(expensesFundId, cashAccountId, BigDecimal("-200.0"), RON, labelsOf("want"))
                )
            )
        val transaction3 =
            transaction(
                userId, dateTime2, listOf(
                    record(expensesFundId, cashAccountId, BigDecimal("-20.0"), RON, labelsOf("other"))
                )
            )
        whenever(fundTransactionSdk.listTransactions(userId, expensesFundId))
            .thenReturn(ListTO.of(transaction1, transaction2, transaction3))

        val reportView = reportViewService.createReportView(userId, reportViewCommand)

        val commandCaptor = argumentCaptor<List<CreateReportRecordCommand>>()
        verify(reportRecordRepository, times(1)).saveAll(commandCaptor.capture())
        commandCaptor.firstValue.let { records ->
            assertThat(records).hasSize(3)
            assertThat(records.map { it.userId }).containsOnly(userId)
            assertThat(records.map { it.reportViewId }).containsOnly(reportView.id)
            assertThat(records.map { it.date })
                .containsExactlyInAnyOrder(dateTime1.date, dateTime2.date, dateTime2.date)
            assertThat(records.map { it.amount })
                .containsExactlyInAnyOrder(BigDecimal("100.0"), BigDecimal("-200.0"), BigDecimal("-20.0"))
        }
    }

    @Test
    fun `create report view should store single fund report records with conversions`(): Unit = runBlocking {
        whenever(reportViewRepository.findByName(userId, reportViewName)).thenReturn(null)
        whenever(reportViewRepository.save(reportViewCommand))
            .thenReturn(
                ReportView(
                    reportViewId, userId, reportViewName, expensesFundId, reportDataConfiguration
                )
            )

        val transaction1 =
            transaction(
                userId, dateTime1, listOf(
                    record(expensesFundId, bankAccountId, BigDecimal("100.0"), EUR, labelsOf("need"))
                )
            )
        whenever(fundTransactionSdk.listTransactions(userId, expensesFundId)).thenReturn(ListTO.of(transaction1))
        val conversionsRequest = ConversionsRequest(
            listOf(ConversionRequest(EUR, RON, dateTime1.date))
        )
        whenever(historicalPricingSdk.convert(userId, conversionsRequest))
            .thenReturn(ConversionsResponse(listOf(ConversionResponse(EUR, RON, dateTime1.date, BigDecimal("5.0")))))

        val reportView = reportViewService.createReportView(userId, reportViewCommand)

        val commandCaptor = argumentCaptor<List<CreateReportRecordCommand>>()
        verify(reportRecordRepository, times(1)).saveAll(commandCaptor.capture())
        commandCaptor.firstValue.let { records ->
            assertThat(records).hasSize(1)
            val record = records.first()
            assertThat(record.userId).isEqualTo(userId)
            assertThat(record.reportViewId).isEqualTo(reportView.id)
            assertThat(record.date).isEqualTo(dateTime1.date)
            assertThat(record.amount).isEqualTo(BigDecimal("100.0"))
            assertThat(record.reportCurrencyAmount).isEqualByComparingTo(BigDecimal("500.0"))
            assertThat(record.unit).isEqualTo(EUR)
            assertThat(record.labels).containsExactlyInAnyOrder(Label("need"))
        }
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
                features = ReportDataFeaturesConfiguration(
                    groupedNet = GenericReportFeature(enabled = true)
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
                features = ReportDataFeaturesConfiguration(
                    groupedBudget = GroupedBudgetReportFeature(enabled = true, distributions = emptyList())
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
                features = ReportDataFeaturesConfiguration(
                    groupedBudget = GroupedBudgetReportFeature(enabled = true, distributions = emptyList())
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
                features = ReportDataFeaturesConfiguration(
                    groupedBudget = GroupedBudgetReportFeature(enabled = true, distributions = listOf(
                        GroupedBudgetReportFeature.BudgetDistribution(
                            default = false,
                            from = YearMonth(2020, 2),
                            groups = listOf(
                                GroupedBudgetReportFeature.GroupBudgetPercentage("need", 50),
                                GroupedBudgetReportFeature.GroupBudgetPercentage("want", 50)
                            )
                        )
                    ))
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
                features = ReportDataFeaturesConfiguration(
                    groupedBudget = GroupedBudgetReportFeature(enabled = true, distributions = listOf(
                        GroupedBudgetReportFeature.BudgetDistribution(
                            default = true,
                            from = YearMonth(2020, 2),
                            groups = listOf(
                                GroupedBudgetReportFeature.GroupBudgetPercentage("need", 50),
                                GroupedBudgetReportFeature.GroupBudgetPercentage("want", 50)
                            )
                        ),
                        GroupedBudgetReportFeature.BudgetDistribution(
                            default = true,
                            from = YearMonth(2020, 2),
                            groups = listOf(
                                GroupedBudgetReportFeature.GroupBudgetPercentage("need", 50),
                                GroupedBudgetReportFeature.GroupBudgetPercentage("want", 50)
                            )
                        )
                    ))
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
                features = ReportDataFeaturesConfiguration(
                    groupedBudget = GroupedBudgetReportFeature(enabled = true, distributions = listOf(
                        GroupedBudgetReportFeature.BudgetDistribution(
                            default = true,
                            from = YearMonth(2020, 2),
                            groups = listOf(
                                GroupedBudgetReportFeature.GroupBudgetPercentage("need", 50),
                                GroupedBudgetReportFeature.GroupBudgetPercentage("want", 50)
                            )
                        ),
                        GroupedBudgetReportFeature.BudgetDistribution(
                            default = false,
                            from = null,
                            groups = listOf(
                                GroupedBudgetReportFeature.GroupBudgetPercentage("need", 50),
                                GroupedBudgetReportFeature.GroupBudgetPercentage("want", 50)
                            )
                        )
                    ))
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
                features = ReportDataFeaturesConfiguration(
                    groupedBudget = GroupedBudgetReportFeature(enabled = true, distributions = listOf(
                        GroupedBudgetReportFeature.BudgetDistribution(
                            default = true,
                            from = null,
                            groups = listOf(
                                GroupedBudgetReportFeature.GroupBudgetPercentage("need", 50),
                                GroupedBudgetReportFeature.GroupBudgetPercentage("want", 50)
                            )
                        ),
                        GroupedBudgetReportFeature.BudgetDistribution(
                            default = false,
                            from = YearMonth(2020, 2),
                            groups = listOf(
                                GroupedBudgetReportFeature.GroupBudgetPercentage("need", 50),
                                GroupedBudgetReportFeature.GroupBudgetPercentage("want", 50)
                            )
                        ),
                        GroupedBudgetReportFeature.BudgetDistribution(
                            default = false,
                            from = YearMonth(2020, 2),
                            groups = listOf(
                                GroupedBudgetReportFeature.GroupBudgetPercentage("need", 60),
                                GroupedBudgetReportFeature.GroupBudgetPercentage("want", 40)
                            )
                        )
                    ))
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
                features = ReportDataFeaturesConfiguration(
                    groupedBudget = GroupedBudgetReportFeature(enabled = true, distributions = listOf(
                        GroupedBudgetReportFeature.BudgetDistribution(
                            default = true,
                            from = null,
                            groups = listOf(
                                GroupedBudgetReportFeature.GroupBudgetPercentage("need", 50),
                                GroupedBudgetReportFeature.GroupBudgetPercentage("want", 50)
                            )
                        ),
                        GroupedBudgetReportFeature.BudgetDistribution(
                            default = false,
                            from = YearMonth(2020, 2),
                            groups = listOf(
                                GroupedBudgetReportFeature.GroupBudgetPercentage("need", 50)
                            )
                        )
                    ))
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
                features = ReportDataFeaturesConfiguration(
                    groupedBudget = GroupedBudgetReportFeature(enabled = true, distributions = listOf(
                        GroupedBudgetReportFeature.BudgetDistribution(
                            default = true,
                            from = null,
                            groups = listOf(
                                GroupedBudgetReportFeature.GroupBudgetPercentage("need", 50),
                                GroupedBudgetReportFeature.GroupBudgetPercentage("want", 50)
                            )
                        ),
                        GroupedBudgetReportFeature.BudgetDistribution(
                            default = false,
                            from = YearMonth(2020, 2),
                            groups = listOf(
                                GroupedBudgetReportFeature.GroupBudgetPercentage("need", 50),
                                GroupedBudgetReportFeature.GroupBudgetPercentage("want", 50),
                                GroupedBudgetReportFeature.GroupBudgetPercentage("other", 50)
                            )
                        )
                    ))
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
                features = ReportDataFeaturesConfiguration(
                    groupedBudget = GroupedBudgetReportFeature(enabled = true, distributions = listOf(
                        GroupedBudgetReportFeature.BudgetDistribution(
                            default = true,
                            from = null,
                            groups = listOf(
                                GroupedBudgetReportFeature.GroupBudgetPercentage("need", 50),
                                GroupedBudgetReportFeature.GroupBudgetPercentage("want", 50)
                            )
                        ),
                        GroupedBudgetReportFeature.BudgetDistribution(
                            default = false,
                            from = YearMonth(2020, 2),
                            groups = listOf(
                                GroupedBudgetReportFeature.GroupBudgetPercentage("need", 50),
                                GroupedBudgetReportFeature.GroupBudgetPercentage("want", 40)
                            )
                        )
                    ))
                )
            )
        )
        whenever(reportViewRepository.findByName(userId, reportViewName)).thenReturn(null)

        assertThatThrownBy { runBlocking { reportViewService.createReportView(userId, reportViewCommand) } }
            .isInstanceOf(ReportingException.GroupBudgetPercentageSumInvalid::class.java)
    }
}
