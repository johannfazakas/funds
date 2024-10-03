package ro.jf.funds.importer.service.domain.service.parser

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader

class CsvParser {
    fun parse(fileContent: String, delimiter: Char = ';', newLine: Char = '\n'): List<Map<String, String>> {
        val csvReader = csvReader {
            this.delimiter = delimiter
        }
        val lines: List<Map<String, String>> = csvReader.readAllWithHeader(fileContent)
        return lines
    }
}
