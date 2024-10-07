package ro.jf.funds.importer.service.domain.service.parser

import ro.jf.funds.importer.service.domain.model.ImportConfiguration
import ro.jf.funds.importer.service.domain.model.ImportTransaction

interface ImportParser {
    fun parse(importConfiguration: ImportConfiguration, files: List<String>): List<ImportTransaction>
}