package ro.jf.funds.importer.service.adapter.mapper

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import ro.jf.funds.importer.api.model.ImportConfigurationRequest
import ro.jf.funds.importer.service.domain.model.ImportConfiguration
import ro.jf.funds.importer.service.domain.model.ImportItem
import java.text.NumberFormat.getNumberInstance

@OptIn(FormatStringsInDatetimeFormats::class)
fun Map<String, String>.toImportItem(
    keys: ImportConfigurationRequest.Keys,
    formatting: ImportConfigurationRequest.Formatting
): ImportItem {
    // TODO(Johann) these format objects are instantiated for each element, could be deduplicated
    val dateTimeFormat = LocalDateTime.Format { byUnicodePattern(formatting.dateTimeFormat) }
    val numberFormat = getNumberInstance(formatting.locale)

    return ImportItem(
        transactionId = this.getValue(keys.transactionId),
        amount = numberFormat.parse(this.getValue(keys.amount)).toDouble().toBigDecimal(),
        accountName = this.getValue(keys.accountName),
        date = LocalDateTime.parse(this.getValue(keys.date), dateTimeFormat)
    )
}

// TODO(Johann) this will make sense later
fun ImportConfigurationRequest.toImportConfiguration() = ImportConfiguration(
    accounts = listOf()
)
