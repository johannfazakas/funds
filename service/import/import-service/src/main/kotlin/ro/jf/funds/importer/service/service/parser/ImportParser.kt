package ro.jf.funds.importer.service.service.parser

import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.service.domain.ImportParsedTransaction

interface ImportParser {
    fun parse(importConfiguration: ImportConfigurationTO, files: List<String>): List<ImportParsedTransaction>
}