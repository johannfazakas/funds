package ro.jf.funds.importer.service.web.mapper

import ro.jf.bk.commons.model.ProblemTO
import ro.jf.funds.importer.service.domain.ImportDataException
import ro.jf.funds.importer.service.domain.ImportException
import ro.jf.funds.importer.service.domain.ImportFormatException

fun ImportException.toProblem(): ProblemTO = ProblemTO(
    title = when (this) {
        is ImportFormatException -> "Invalid import format"
        is ImportDataException -> "Invalid import data"
    },
    detail = this.message ?: "No message"
)
