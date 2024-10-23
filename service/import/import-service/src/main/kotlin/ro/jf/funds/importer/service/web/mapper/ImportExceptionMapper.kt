package ro.jf.funds.importer.service.web.mapper

import ro.jf.funds.commons.model.ProblemTO
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.importer.service.domain.exception.ImportException
import ro.jf.funds.importer.service.domain.exception.ImportFormatException
import ro.jf.funds.importer.service.domain.exception.MissingImportConfigurationException

fun ImportException.toProblem(): ProblemTO = ImportProblem(
    title = when (this) {
        is ImportFormatException -> "Invalid import format"
        is ImportDataException -> "Invalid import data"
        is MissingImportConfigurationException -> "Missing import configuration"
    },
    detail = this.message ?: "No message"
)

// TODO(Johann) not here, I'm just not sure what is the best pattern yet for these problems & exceptions
class ImportProblem(
    override val title: String,
    override val detail: String
) : ProblemTO()
