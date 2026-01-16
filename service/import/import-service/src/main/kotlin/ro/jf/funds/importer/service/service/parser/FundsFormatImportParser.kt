package ro.jf.funds.importer.service.service.parser

import ro.jf.funds.importer.service.domain.exception.ImportDataException

class FundsFormatImportParser(
    private val csvParser: CsvParser,
) : ImportParser() {

    override fun parseItems(files: List<String>): List<ImportItem> {
        val items = files.flatMap { csvParser.parse(it) }.map { FundsFormatImportItem(it) }
        if (items.isEmpty())
            throw ImportDataException("No import reportdata")
        return items
    }
}
