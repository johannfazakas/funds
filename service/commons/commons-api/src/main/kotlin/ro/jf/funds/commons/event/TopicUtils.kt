package ro.jf.funds.commons.event

// TODO(Johann) make this buildable from different segments
@JvmInline
value class Topic(val value: String)

@JvmInline
value class Domain(val value: String)

@JvmInline
value class EventType(val value: String)

private const val APPLICATION_NAME = "funds"

class TopicSupplier(val environment: String) {
    fun getTopic(domain: Domain, eventType: EventType): Topic {
        return Topic("$environment.$APPLICATION_NAME.$environment.${domain.value}.${eventType.value}")
    }
}
