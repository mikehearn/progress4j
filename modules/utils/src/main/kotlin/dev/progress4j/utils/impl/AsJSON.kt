package dev.progress4j.utils.impl

private const val NEWLINE: Int = '\n'.code
private const val BACKSLASH: Int = '\\'.code
private const val DOUBLE_QUOTE: Int = '"'.code

/**
 * A quick and dirty one-line JSON renderer, without dependencies. The map can only contain strings, numbers and other maps of string->object.
 */
internal val Map<String, Any?>.asJSON: String
    get() = buildString { asJSON(this) }

/**
 * A quick and dirty one-line JSON renderer, without dependencies. The map can only contain strings, numbers and other maps of string->object.
 */
internal fun Map<String, Any?>.asJSON(to: StringBuilder): Map<String, Any?> {
    with(to) {
        append("{ ")
        val entries = this@asJSON.entries.toList()
        var first = true
        for ((key, value) in entries) {
            if (value == null) continue
            if (first) {
                first = false
            } else {
                append(", ")
            }
            append('"')
            append(key)
            append("\": ")
            appendJSONValue(value)
        }
        append(" }")
    }
    return this
}

private fun StringBuilder.appendJSONValue(value: Any?) {
    when (value) {
        is String -> {
            append('"')
            value.codePoints().forEach {
                when {
                    it == DOUBLE_QUOTE -> append("\\\"")
                    it == BACKSLASH -> append("\\\\")
                    it == NEWLINE -> append("\\n")
                    it < 20 -> append("\\u00${it.toString(16).padStart(2, '0')}")
                    else -> appendCodePoint(it)
                }
            }
            append('"')
        }

        is Number -> append(value)
        is Boolean -> append(value)
        is Map<*, *> -> {
            if (value.isEmpty()) {
                append("{}")
            } else {
                require(value.entries.first().key is String) { "Cannot render any map type except Map<String, Any>" }
                @Suppress("UNCHECKED_CAST")
                append((value as Map<String, Any>).asJSON)
            }
        }

        is List<Any?> -> {
            if (value.isEmpty()) {
                append("[]")
            } else {
                append("[ ")
                for ((index, item) in value.withIndex()) {
                    appendJSONValue(item)
                    if (index < value.lastIndex)
                        append(", ")
                }
                append(" ]")
            }
        }

        null -> append("null")
        else -> error("Unsupported type in json map: ${value.javaClass.name}")
    }
}
