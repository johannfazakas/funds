package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class ImportConfigurationRequest(
    val keys: Keys,
    val formatting: Formatting,
) {
    @Serializable
    data class Keys(
        val amount: String,
        val transactionId: String,
        val date: String,
        val accountName: String
    )

    @Serializable
    data class Formatting(
        val csvDelimiter: String,
        val dateTimeFormat: String,
        @Serializable(with = ro.jf.bk.commons.serialization.LocaleSerializer::class)
        val locale: Locale
    )
}
