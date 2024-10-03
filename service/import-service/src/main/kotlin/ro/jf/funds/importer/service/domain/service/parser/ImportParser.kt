package ro.jf.funds.importer.service.domain.service.parser

import ro.jf.funds.importer.service.domain.model.ImportItem

interface ImportParser {
    fun parse(files: List<String>): List<ImportItem>
}