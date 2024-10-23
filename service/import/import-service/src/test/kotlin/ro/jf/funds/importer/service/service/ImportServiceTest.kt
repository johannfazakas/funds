package ro.jf.funds.importer.service.service

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ro.jf.funds.importer.api.model.ImportConfigurationTO
import ro.jf.funds.importer.api.model.ImportFileTypeTO
import ro.jf.funds.importer.service.domain.ImportTransaction
import ro.jf.funds.importer.service.service.parser.ImportParser
import ro.jf.funds.importer.service.service.parser.ImportParserRegistry
import java.util.UUID.randomUUID

class ImportServiceTest {
    private val importParserRegistry = mock<ImportParserRegistry>()
    private val importParser = mock<ImportParser>()
    private val importHandler = mock<ImportHandler>()

    private val importService = ImportService(importParserRegistry, importHandler)

    @Test
    fun `should parse and handle import files`() = runBlocking {
        val userId = randomUUID()
        val importType = ImportFileTypeTO.WALLET_CSV
        val configuration = ImportConfigurationTO(importType, emptyList(), emptyList())
        val importFiles = listOf("fileContent1", "fileContent2")
        whenever(importParserRegistry[importType]).thenReturn(importParser)
        val importItems = mock<List<ImportTransaction>>()
        whenever(importParser.parse(configuration, importFiles)).thenReturn(importItems)

        importService.import(userId, configuration, importFiles)

        verify(importHandler, times(1)).import(userId, importItems)
    }
}
