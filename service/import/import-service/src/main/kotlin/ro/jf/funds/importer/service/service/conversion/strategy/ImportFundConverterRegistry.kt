package ro.jf.funds.importer.service.service.conversion.strategy

import ro.jf.funds.importer.service.service.conversion.ImportFundConverter

class ImportFundConverterRegistry(
    val converters: List<ImportFundConverter>
)
