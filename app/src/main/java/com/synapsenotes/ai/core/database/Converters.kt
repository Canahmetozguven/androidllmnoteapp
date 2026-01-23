package com.synapsenotes.ai.core.database

import androidx.room.TypeConverter
import org.json.JSONArray

class Converters {

    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return try {
            val jsonArray = JSONArray(value)
            List(jsonArray.length()) { jsonArray.getString(it) }
        } catch (e: Exception) {
            // Fallback for legacy comma-separated values
            value.split(",").map { it.trim() }
        }
    }

    @TypeConverter
    fun toStringList(list: List<String>?): String {
        if (list.isNullOrEmpty()) return ""
        return JSONArray(list).toString()
    }

    @TypeConverter
    fun fromFloatList(value: String?): List<Float>? {
        if (value.isNullOrEmpty()) return null
        return try {
            value.split(",").map { it.trim().toFloat() }
        } catch (e: NumberFormatException) {
            null
        }
    }

    @TypeConverter
    fun toFloatList(list: List<Float>?): String? {
        if (list == null) return null
        return list.joinToString(",")
    }
}
