package ro.jf.funds.importer.api.model

// TODO(Johann) could be removed, right?
data class CsvFormattingTO(
    val encoding: String = "UTF-8",
    val delimiter: String = ";"
)
