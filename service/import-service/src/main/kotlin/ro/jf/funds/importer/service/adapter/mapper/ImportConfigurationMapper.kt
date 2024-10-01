package ro.jf.funds.importer.service.adapter.mapper

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.service.domain.model.ImportConfiguration
import ro.jf.funds.importer.service.domain.model.ImportItem
import java.text.NumberFormat.getNumberInstance

@OptIn(FormatStringsInDatetimeFormats::class)
fun Map<String, String>.toImportItem(
    importConfiguration: ImportConfigurationTO
): ImportItem {
    // TODO(Johann) these format objects are instantiated for each element, could be deduplicated
    val dateTimeFormat = LocalDateTime.Format { byUnicodePattern(importConfiguration.formatting.dateTimeFormat) }
    val numberFormat = getNumberInstance(importConfiguration.formatting.locale)

    return ImportItem(
        amount = numberFormat.parse(this.getValue(importConfiguration.keys.amount)).toDouble().toBigDecimal(),
        currency = this.getValue(importConfiguration.keys.currency),
        accountName = this.getValue(importConfiguration.keys.accountName),
        date = LocalDateTime.parse(this.getValue(importConfiguration.keys.date), dateTimeFormat),
        // TODO(Johann) should get back to this
        transactionId = "asdf"
    )
}

// TODO(Johann) this will make sense later
fun ImportConfigurationTO.toImportConfiguration() = ImportConfiguration(
    accounts = listOf()
)
