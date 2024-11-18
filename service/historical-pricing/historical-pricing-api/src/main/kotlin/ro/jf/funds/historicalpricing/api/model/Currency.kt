package ro.jf.funds.historicalpricing.api.model

import kotlinx.serialization.Serializable

// TODO(Johann) this should be removed, there's already one in commons
@Serializable
enum class Currency {
    RON,
    EUR
}