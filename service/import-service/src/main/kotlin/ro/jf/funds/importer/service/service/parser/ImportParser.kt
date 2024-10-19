package ro.jf.funds.importer.service.service.parser

import ro.jf.funds.importer.service.domain.ImportConfiguration
import ro.jf.funds.importer.service.domain.ImportTransaction

interface ImportParser {
    fun parse(importConfiguration: ImportConfiguration, files: List<String>): List<ImportTransaction>
}