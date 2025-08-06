package ro.jf.funds.importer.service.service.conversion.strategy

import ro.jf.funds.importer.service.service.conversion.ImportFundConverter

// TODO(Johann-40) this is rather an import transaction converter
class ImportFundConverterRegistry(
    val converters: List<ImportFundConverter>
)
