package ro.jf.funds.importer.service.domain.service

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import ro.jf.funds.importer.service.domain.model.ImportConfiguration
import ro.jf.funds.importer.service.domain.model.ImportItem
import ro.jf.funds.importer.service.domain.model.ImportType
import ro.jf.funds.importer.service.domain.service.parser.ImportParser
import ro.jf.funds.importer.service.domain.service.parser.ImportParserRegistry
import java.util.UUID.randomUUID

class ImportServiceTest {
    private val importParserRegistry = mock<ImportParserRegistry>()
    private val importParser = mock<ImportParser>()
    private val importHandler = mock<ImportHandler>()

    private val importService = ImportServiceImpl(importParserRegistry, importHandler)

    @Test
    fun `should parse and handle import files`() = runBlocking {
        val userId = randomUUID()
        val importType = ImportType.WALLET_CSV
        val configuration = ImportConfiguration(importType, emptyList())
        val importFiles = listOf("fileContent1", "fileContent2")
        whenever(importParserRegistry[importType]).thenReturn(importParser)
        val importItems = mock<List<ImportItem>>()
        whenever(importParser.parse(configuration, importFiles)).thenReturn(importItems)

        importService.import(userId, configuration, importFiles)

        verify(importHandler, times(1)).import(userId, importItems)
    }
}
