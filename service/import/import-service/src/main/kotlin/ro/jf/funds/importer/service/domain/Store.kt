package ro.jf.funds.importer.service.domain

import ro.jf.funds.importer.service.domain.exception.ImportDataException

class Store<K, V>(
    private val data: Map<K, V>,
) {
    operator fun get(key: K): V = data[key] ?: throw ImportDataException("Record not found: $key")
}