package ro.jf.funds.platform.jvm.persistence

data class PagedResult<T>(
    val items: List<T>,
    val total: Long,
)
