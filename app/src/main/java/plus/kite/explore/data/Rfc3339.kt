package plus.kite.explore.data

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/** The server's timestamps: RFC 3339, with or without fractional seconds of any length. */
object Rfc3339 {
    fun parse(text: String): Instant? = try {
        OffsetDateTime.parse(text).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }
}

object Rfc3339Serializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Rfc3339", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        val raw = decoder.decodeString()
        return Rfc3339.parse(raw) ?: throw SerializationException("Not an RFC 3339 time: $raw")
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }
}

/** How every response is read: unknown fields and nulls in place of defaults are fine. */
val ExploreJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}
