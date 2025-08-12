package ro.jf.funds.importer.service.service.conversion.strategy

import ro.jf.funds.importer.service.service.conversion.ImportTransactionConverter

class ImportTransactionConverterRegistry(
    val converters: List<ImportTransactionConverter>
)
