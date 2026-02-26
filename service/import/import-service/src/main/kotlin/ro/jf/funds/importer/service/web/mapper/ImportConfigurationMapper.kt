package ro.jf.funds.importer.service.web.mapper

import kotlinx.datetime.toKotlinLocalDateTime
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.service.domain.ImportConfiguration

fun ImportConfiguration.toTO(): ImportConfigurationTO {
    val matcherTOs = matchers.toMatcherTOs()
    return ImportConfigurationTO(
        importConfigurationId = importConfigurationId,
        name = name,
        accountMatchers = matcherTOs.accountMatchers,
        fundMatchers = matcherTOs.fundMatchers,
        exchangeMatchers = matcherTOs.exchangeMatchers,
        labelMatchers = matcherTOs.labelMatchers,
        createdAt = createdAt.toKotlinLocalDateTime(),
    )
}
