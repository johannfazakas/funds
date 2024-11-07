package ro.jf.funds.commons.event

import java.util.*

data class Event<T>(
    val userId: UUID,
    val key: String,
    val payload: T
)

data class RpcRequest<T>(
    val userId: UUID,
    val correlationId: UUID,
    val key: String,
    val payload: T
)

data class RpcResponse<T>(
    val userId: UUID,
    val correlationId: UUID,
    val key: String,
    val payload: T
)
