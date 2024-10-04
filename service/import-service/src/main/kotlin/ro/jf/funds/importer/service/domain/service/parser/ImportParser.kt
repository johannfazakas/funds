package ro.jf.funds.importer.service.domain.service.parser

import ro.jf.funds.importer.service.domain.model.ImportItem
import ro.jf.funds.importer.service.domain.model.ImportConfiguration

interface ImportParser {
    fun parse(importConfiguration: ImportConfiguration, files: List<String>): List<ImportItem>
}