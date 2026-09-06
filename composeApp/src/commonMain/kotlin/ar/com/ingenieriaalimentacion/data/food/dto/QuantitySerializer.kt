package ar.com.ingenieriaalimentacion.data.food.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonUnquotedLiteral

/**
 * Writes an already-normalized decimal `String` ("12.5", "0.750") as a **JSON number**, not a
 * quoted string, without ever converting through `Double`. The server stores quantity in a
 * `Numeric(10,3)` balance it accumulates, so no float noise may reach it.
 *
 * Reading accepts a JSON number or a string alike — `decodeString` is lenient about both.
 */
@OptIn(ExperimentalSerializationApi::class)
object QuantitySerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("Quantity", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        val json = encoder as? JsonEncoder
            ?: error("QuantitySerializer requires a JSON encoder")
        json.encodeJsonElement(JsonUnquotedLiteral(value))
    }

    override fun deserialize(decoder: Decoder): String = decoder.decodeString()
}
