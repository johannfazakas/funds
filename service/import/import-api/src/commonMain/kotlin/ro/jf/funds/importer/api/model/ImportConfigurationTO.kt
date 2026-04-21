package ro.jf.funds.importer.api.model

import com.benasher44.uuid.Uuid
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import ro.jf.funds.platform.api.serialization.UuidSerializer

@Serializable
data class ImportConfigurationTO(
    @Serializable(with = UuidSerializer::class)
    val importConfigurationId: Uuid,
    val name: String,
    val accountMatchers: List<AccountMatcherTO> = emptyList(),
    val fundMatchers: List<FundMatcherTO> = emptyList(),
    val exchangeMatchers: List<ExchangeMatcherTO> = emptyList(),
    val categoryMatchers: List<CategoryMatcherTO> = emptyList(),
    val createdAt: LocalDateTime,
)
