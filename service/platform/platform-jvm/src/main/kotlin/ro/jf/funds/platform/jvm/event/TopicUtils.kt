package ro.jf.funds.platform.jvm.event

import ro.jf.funds.platform.api.event.EventType

private const val APPLICATION_NAME = "funds"

@JvmInline
value class Topic(val value: String)

class TopicSupplier(val environment: String) {
    fun topic(eventType: EventType): Topic =
        Topic("$APPLICATION_NAME.$environment.${eventType.domain}.${eventType.resource}.${eventType.operation}")
}
