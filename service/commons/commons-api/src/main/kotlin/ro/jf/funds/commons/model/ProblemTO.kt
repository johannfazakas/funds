package ro.jf.funds.commons.model

import kotlinx.serialization.Serializable

@Serializable
abstract class ProblemTO : RuntimeException() {
    abstract val title: String
    // TODO(Johann) might be not nullable?
    abstract val detail: String?
}
