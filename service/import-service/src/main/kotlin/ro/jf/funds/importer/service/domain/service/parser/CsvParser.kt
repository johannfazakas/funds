package ro.jf.funds.importer.service.domain.service.parser

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader

@JvmInline
value class CsvRow(private val values: Map<String, String>) {
    operator fun get(column: String): String? = values[column]
}

class CsvParser {
    fun parse(fileContent: String, delimiter: Char = ';', newLine: Char = '\n'): List<CsvRow> {
        val csvReader = csvReader {
            this.delimiter = delimiter
        }
        val lines = csvReader.readAllWithHeader(fileContent).map(::CsvRow)
        return lines
    }
}
