package ro.jf.funds.commons.event

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.apache.kafka.common.serialization.Deserializer
import org.apache.kafka.common.serialization.Serializer
//
//class EventSerializer<T>(private val clazz: Class<T>) : Serializer<T> {
//    private val json = Json {
//        ignoreUnknownKeys = true
//        encodeDefaults = true
//    }
//
//    @Suppress("UNCHECKED_CAST")
//    private val serializer: KSerializer<T> = serializer(clazz) as KSerializer<T>
//
//    override fun serialize(topic: String?, data: T?): ByteArray? {
//        if (data == null) return null
//
//        return try {
//            serializer(clazz) as KSerializer<T>
//            json.encodeToString(serializer, data).toByteArray(Charsets.UTF_8)
//        } catch (e: Exception) {
//            throw RuntimeException("Error serializing $data", e)
//        }
//    }
//}
//
//class EventDeserializer<T>(private val clazz: Class<T>) : Deserializer<T> {
//    private val json = Json {
//        ignoreUnknownKeys = true
//        encodeDefaults = true
//    }
//
//    @Suppress("UNCHECKED_CAST")
//    private val serializer: KSerializer<T> = serializer(clazz) as KSerializer<T>
//
//    override fun deserialize(topic: String?, data: ByteArray?): T? {
//        if (data == null) return null
//
//        return try {
//            val jsonString = String(data, Charsets.UTF_8)
//            json.decodeFromString(serializer, jsonString)
//        } catch (e: Exception) {
//            throw RuntimeException("Error deserializing data", e)
//        }
//    }
//}
