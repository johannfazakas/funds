package ro.jf.funds.commons.event

private const val APPLICATION_NAME = "funds"

@JvmInline
value class Topic(val value: String) {
    companion object {
        fun create(environment: String, domain: Domain, eventType: EventType): Topic {
            return Topic("$environment.$APPLICATION_NAME.${domain.value}.${eventType.value}")
        }
    }
}

@JvmInline
value class Domain(val value: String)

@JvmInline
value class EventType(val value: String)


class TopicSupplier(val environment: String) {
    fun topic(domain: Domain, eventType: EventType): Topic {
        return Topic.create(environment, domain, eventType)
    }
}
