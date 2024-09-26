package ro.jf.funds.importer.service.adapter.file

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import mu.KotlinLogging.logger
import ro.jf.funds.importer.api.model.CsvFormattingTO

private val log = logger { }

class CsvParser {
    fun parseCsv(csvLines: List<String>, csvFormatting: CsvFormattingTO): List<Map<String, String>> {
        val csvReader = csvReader {
            delimiter = ';'
        }
        val content = csvLines.joinToString("\n")
        val lines: List<Map<String, String>> = csvReader.readAllWithHeader(content)
        log.info { "Read csv: $lines" }
        return lines
    }
}
