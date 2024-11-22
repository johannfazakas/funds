package ro.jf.funds.importer.service.service.conversion.converter

import ro.jf.funds.importer.service.service.conversion.ImportFundTransaction
import ro.jf.funds.importer.service.service.conversion.ImportFundTransaction.Type.*

// TODO(Johann) is there a way to inject all beans by type in a list in koin? like in Spring?
class ImportFundConverterRegistry(
    private val singleRecordFundConverter: SingleRecordFundConverter,
    private val transferFundConverter: TransferFundConverter,
    private val implicitTransferFundConverter: ImplicitTransferFundConverter,
    private val exchangeSingleFundConverter: ExchangeSingleFundConverter
) {
    operator fun get(type: ImportFundTransaction.Type): ImportFundConverter {
        return when (type) {
            SINGLE_RECORD -> singleRecordFundConverter
            TRANSFER -> transferFundConverter
            IMPLICIT_TRANSFER -> implicitTransferFundConverter
            EXCHANGE -> exchangeSingleFundConverter
        }
    }

    fun all(): List<ImportFundConverter> = ImportFundTransaction.Type.entries.map { get(it) }
}
