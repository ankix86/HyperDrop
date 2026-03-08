package com.lantransfer.app.util

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

fun JsonElement.asObject(): JsonObject = this as JsonObject
fun JsonObject.str(key: String): String = (this[key] as JsonPrimitive).content
fun JsonObject.bool(key: String): Boolean = (this[key] as JsonPrimitive).content.toBoolean()
fun JsonObject.int(key: String): Int = (this[key] as JsonPrimitive).content.toInt()
fun JsonObject.long(key: String): Long = (this[key] as JsonPrimitive).content.toLong()
