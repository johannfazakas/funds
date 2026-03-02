package ro.jf.funds.importer.service.service.parser

import ro.jf.funds.importer.service.domain.exception.ImportDataException

class WalletCsvImportParser(
    private val csvParser: CsvParser,
) : ImportParser() {

    override fun parseItems(content: String): List<ImportItem> {
        val items = csvParser.parse(content).map { WalletImportItem(it) }
        if (items.isEmpty())
            throw ImportDataException("No import reportdata")
        return items
    }
}
