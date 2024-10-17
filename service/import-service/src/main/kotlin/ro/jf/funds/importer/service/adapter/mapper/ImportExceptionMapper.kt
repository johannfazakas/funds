package ro.jf.funds.importer.service.adapter.mapper

import ro.jf.bk.commons.model.ProblemTO
import ro.jf.funds.importer.service.domain.exception.ImportDataException
import ro.jf.funds.importer.service.domain.exception.ImportException
import ro.jf.funds.importer.service.domain.exception.ImportFormatException

fun ImportException.toProblem(): ProblemTO = ProblemTO(
    title = when (this) {
        is ImportFormatException -> "Invalid import format"
        is ImportDataException -> "Invalid import data"
    },
    detail = this.message ?: "No message"
)
