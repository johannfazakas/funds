package ro.jf.funds.commons.api.event

data class EventType(
    val domain: String,
    val resource: String,
    val operation: String
)
