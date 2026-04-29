package ro.jf.funds.importer.service.domain

import com.benasher44.uuid.Uuid
import ro.jf.funds.importer.service.domain.exception.ImportDataException

class Store<V>(
    private val data: Map<Uuid, V>,
) {
    constructor(items: List<V>, keySelector: (V) -> Uuid) : this(items.associateBy(keySelector))

    operator fun get(key: Uuid): V = data[key] ?: throw ImportDataException("Store value not found: $key")
}
