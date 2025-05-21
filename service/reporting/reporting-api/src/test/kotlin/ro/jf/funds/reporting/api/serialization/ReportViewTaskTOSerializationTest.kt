package ro.jf.funds.reporting.api.serialization

import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ro.jf.funds.reporting.api.model.ReportViewTaskTO
import java.util.UUID.randomUUID

class ReportViewTaskTOSerializationTest {

    @Test
    fun `should serialize and deserialize in progress task as base class`() {
        val task: ReportViewTaskTO = ReportViewTaskTO.InProgress(taskId = randomUUID())

        val serializedTask = Json.encodeToString(ReportViewTaskTO.serializer(), task)
        val deserializedTask = Json.decodeFromString(ReportViewTaskTO.serializer(), serializedTask)

        assertThat(deserializedTask).isEqualTo(task)
    }

    @Test
    fun `should serialize and deserialize in progress task as child class`() {
        val task: ReportViewTaskTO.InProgress = ReportViewTaskTO.InProgress(taskId = randomUUID())

        val serializedTask = Json.encodeToString(ReportViewTaskTO.InProgress.serializer(), task)
        val deserializedTask = Json.decodeFromString(ReportViewTaskTO.InProgress.serializer(), serializedTask)

        assertThat(deserializedTask).isEqualTo(task)
    }

}
