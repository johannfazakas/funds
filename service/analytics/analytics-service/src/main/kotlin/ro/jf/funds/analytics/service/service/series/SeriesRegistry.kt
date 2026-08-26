package ro.jf.funds.analytics.service.service.series

import ro.jf.funds.analytics.service.domain.Series

class SeriesRegistry(definitions: List<SeriesDefinition<*>>) {
    private val bySeries: Map<Series<*>, SeriesDefinition<*>> = definitions.associateBy { it.series }

    init {
        require(bySeries.size == definitions.size) {
            val duplicates = definitions.groupBy { it.series }.filterValues { it.size > 1 }.keys
            "Duplicate series definitions: $duplicates"
        }
        validateDependenciesRegistered()
        validateAcyclic()
    }

    operator fun get(series: Series<*>): SeriesDefinition<*> =
        bySeries[series] ?: throw IllegalArgumentException("Series '$series' has no registered definition")

    private fun validateDependenciesRegistered() {
        bySeries.values.forEach { definition ->
            val missing = definition.dependencies.filterNot { it in bySeries }
            require(missing.isEmpty()) {
                "Series '${definition.series}' depends on unregistered series: $missing"
            }
        }
    }

    private fun validateAcyclic() {
        val visited = mutableSetOf<Series<*>>()
        val path = mutableListOf<Series<*>>()

        fun visit(series: Series<*>) {
            if (series in visited) return
            require(series !in path) {
                "Series dependency cycle detected: ${(path + series).joinToString(" -> ")}"
            }
            path.add(series)
            bySeries.getValue(series).dependencies.forEach { visit(it) }
            path.removeLast()
            visited.add(series)
        }

        bySeries.keys.forEach { visit(it) }
    }
}
