package ro.jf.funds.importer.service.service.parser

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.DateTimeFormat
import ro.jf.funds.importer.service.domain.ImportFormatException
import java.math.BigDecimal

@JvmInline
value class CsvRow(private val values: Map<String, String>) {
    fun getString(column: String): String = getRawCell(column)
    fun getStringOrNull(column: String): String? = getRawCellOrNull(column)

    fun getBigDecimal(column: String): BigDecimal = getRawCell(column).toDecimal()
    fun getBigDecimalOrNull(column: String): BigDecimal? = getRawCellOrNull(column)?.toDecimal()

    fun getDateTime(column: String, format: DateTimeFormat<LocalDateTime>) =
        getRawCell(column).toDateTime(format)

    fun getDateTimeOrNull(column: String, format: DateTimeFormat<LocalDateTime>) =
        getRawCellOrNull(column)?.toDateTime(format)

    private fun getRawCell(column: String): String =
        values[column] ?: throw ImportFormatException("Column $column not found on row with values $values")

    private fun getRawCellOrNull(column: String): String? = values[column]

    private fun String.toDateTime(format: DateTimeFormat<LocalDateTime>) = try {
        LocalDateTime.parse(this, format)
    } catch (e: Exception) {
        throw ImportFormatException("Error parsing date $this with format $format", e)
    }

    private fun String.toDecimal() = try {
        this.toBigDecimal()
    } catch (e: Exception) {
        throw ImportFormatException("Error parsing decimal $this", e)
    }

    operator fun get(column: String): String? = values[column]
}

class CsvParser {
    fun parse(fileContent: String, delimiter: Char = ';'): List<CsvRow> {
        val csvReader = csvReader {
            this.delimiter = delimiter
        }
        val lines = csvReader.readAllWithHeader(fileContent).map(::CsvRow)
        return lines
    }
}
