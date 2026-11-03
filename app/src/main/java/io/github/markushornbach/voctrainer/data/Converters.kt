package io.github.markushornbach.voctrainer.data

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String = encodeStringList(value)

    @TypeConverter
    fun toStringList(value: String): List<String> = Json.decodeFromString(value)

    companion object {
        fun encodeStringList(value: List<String>): String = Json.encodeToString(value)
    }
}
