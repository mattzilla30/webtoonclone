package com.webtoonclone.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Reads a map of text values. MangaDex sends `[]`, an empty array, where it means an empty map,
 * for fields such as a description or links that a series does not have. Anything that is not an
 * object reads as an empty map, and values that are not text are skipped.
 */
internal object LenientStringMap : KSerializer<Map<String, String>> {
    private val delegate = MapSerializer(String.serializer(), String.serializer())

    override val descriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Map<String, String>) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): Map<String, String> {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return delegate.deserialize(decoder)
        if (element !is JsonObject) return emptyMap()
        return element.mapNotNull { (key, value) -> (value as? JsonPrimitive)?.contentOrNull?.let { key to it } }.toMap()
    }
}
