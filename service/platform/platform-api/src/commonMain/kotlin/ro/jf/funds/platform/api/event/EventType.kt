package ro.jf.funds.platform.api.event

data class EventType(
    val domain: String,
    val resource: String,
    val operation: String
)
