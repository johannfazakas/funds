package ro.jf.funds.reporting.api.model

import kotlinx.serialization.Serializable
import ro.jf.funds.commons.model.Currency
import ro.jf.funds.commons.model.Label
import ro.jf.funds.commons.serialization.UUIDSerializer
import java.util.*

// TODO(Johann) does this make sense? Couldn't everything be synchronized through kafka and then filter only at request level maybe?
@Serializable
data class ReportViewTO(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    @Serializable(with = UUIDSerializer::class)
    val fundId: UUID,
    val dataConfiguration: ReportDataConfigurationTO,
)
