package ro.jf.funds.importer.api.model

import kotlinx.serialization.Serializable
import java.util.*


@Serializable
data class FormattingTO(
    val csvDelimiter: String,
    val dateTimeFormat: String,
    @Serializable(with = ro.jf.bk.commons.serialization.LocaleSerializer::class)
    val locale: Locale
)
