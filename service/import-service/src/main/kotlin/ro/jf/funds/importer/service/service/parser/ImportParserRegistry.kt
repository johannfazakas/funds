package ro.jf.funds.importer.service.service.parser

import ro.jf.funds.importer.api.model.ImportFileTypeTO

class ImportParserRegistry(
    private val walletCsvImportParser: WalletCsvImportParser
) {
    operator fun get(importType: ImportFileTypeTO): ImportParser =
        when (importType) {
            ImportFileTypeTO.WALLET_CSV -> walletCsvImportParser
        }
}
