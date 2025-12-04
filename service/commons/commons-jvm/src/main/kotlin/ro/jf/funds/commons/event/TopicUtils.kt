package ro.jf.funds.commons.event

import ro.jf.funds.commons.api.event.EventType

private const val APPLICATION_NAME = "funds"

@JvmInline
value class Topic(val value: String)

class TopicSupplier(val environment: String) {
    fun topic(eventType: EventType): Topic =
        Topic("$APPLICATION_NAME.$environment.${eventType.domain}.${eventType.resource}.${eventType.operation}")
}
