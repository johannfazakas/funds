package ro.jf.funds.importer.api.model

data class CsvFormattingTO(
    val encoding: String = "UTF-8",
    val delimiter: String = ";"
)
