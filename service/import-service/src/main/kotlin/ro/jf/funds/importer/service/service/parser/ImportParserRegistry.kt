package ro.jf.funds.importer.service.service.parser

import ro.jf.funds.importer.service.domain.ImportType

class ImportParserRegistry(
    private val walletCsvImportParser: WalletCsvImportParser
) {
    operator fun get(importType: ImportType): ImportParser =
        when (importType) {
            ImportType.WALLET_CSV -> walletCsvImportParser
        }
}
