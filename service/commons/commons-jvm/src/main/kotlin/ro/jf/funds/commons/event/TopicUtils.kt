package ro.jf.funds.commons.event

private const val APPLICATION_NAME = "funds"

@JvmInline
value class Topic(val value: String) {
    companion object {
        fun create(environment: String, resource: Resource, eventType: EventType): Topic {
            return Topic("$environment.$APPLICATION_NAME.${resource.domain.name}.${resource.name}.$eventType")
        }
    }
}

enum class Domain(val value: String) {
    FUND("fund"),
    REPORTING("reporting")
}

enum class Resource(val value: String, val domain: Domain) {
    REPORT_VIEW("report_view", Domain.REPORTING),
}

enum class EventType(val value: String) {
    REQUEST("request"),
    RESPONSE("response")
}

class TopicSupplier(val environment: String) {
    fun topic(resource: Resource, eventType: EventType): Topic {
        return Topic.create(environment, resource, eventType)
    }
}
