package ro.jf.funds.importer.api.event

import ro.jf.funds.platform.api.event.EventType

object ImportEvents {
    private const val IMPORT_DOMAIN = "import"
    private const val IMPORT_FILE_RESOURCE = "import-file"
    private const val IMPORT_COMMAND = "import-command"

    val ImportFileCommand = EventType(IMPORT_DOMAIN, IMPORT_FILE_RESOURCE, IMPORT_COMMAND)
}
