package ro.jf.funds.importer.service.service.parser

import ro.jf.funds.importer.service.domain.exception.ImportDataException

class FundsFormatImportParser(
    private val csvParser: CsvParser,
) : ImportParser() {

    override fun parseItems(content: String): List<ImportItem> {
        val items = csvParser.parse(content).map { FundsFormatImportItem(it) }
        if (items.isEmpty())
            throw ImportDataException("No import reportdata")
        return items
    }
}
