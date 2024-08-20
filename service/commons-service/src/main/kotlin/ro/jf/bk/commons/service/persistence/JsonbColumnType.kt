package ro.jf.bk.commons.service.persistence

import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.postgresql.util.PGobject
import java.sql.ResultSet

fun Table.jsonb(name: String): Column<Map<String, String>> = registerColumn(name = name, type = JsonbColumnType())

class JsonbColumnType(
) : ColumnType() {
    override fun sqlType() = "jsonb"

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        // value passed is the result of notNullValueToDB()
        val pgObject = PGobject().apply {
            type = "jsonb"
            this.value = (value as? JsonObject)?.toString()
        }
        stmt[index] = pgObject
    }

    override fun notNullValueToDB(value: Any): Any {
        return when (value) {
            is Map<*, *> -> {
                val jsonObject = value.mapKeys { (key, _) -> key.toString() }.toJsonObject()
                if (jsonObject.any { (_, value) -> value !is JsonPrimitive || !value.isString }) {
                    error("Only String values are supported in the JSON object")
                }
                jsonObject
            }

            else -> error("Unexpected value: $value, expecting a Map<String, String?>")
        }
    }

    override fun readObject(rs: ResultSet, index: Int): Any {
        return Json.parseToJsonElement(rs.getString(index)).jsonObject.toMap()
    }

    private fun Map<*, *>?.toJsonObject(): JsonObject = this
        ?.map { (key, value) -> key.toString() to (value?.toJsonElement() ?: JsonNull) }
        ?.toMap()
        ?.let(::JsonObject)
        ?: JsonObject(emptyMap())

    private fun JsonObject.toMap(): Map<String, String> {
        return jsonObject.mapValues { (_, element) ->
            when (element) {
                is JsonPrimitive -> element.content
                else -> element.toString()
            }
        }
    }

    private fun Any.toJsonElement(): JsonElement = when (this) {
        is String -> JsonPrimitive(this)
        else -> error("Type not supported: $this")
    }
}
