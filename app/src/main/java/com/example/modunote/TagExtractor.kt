package com.example.modunote

object TagExtractor {
    private val tagRegex = Regex("""#([\wÀ-ɏ]+(?:/[\wÀ-ɏ]+)*)""")

    fun extract(text: String): List<String> {
        val tags = mutableSetOf<String>()
        tagRegex.findAll(text).forEach { match ->
            val full = match.groupValues[1]
            val parts = full.split("/")
            for (i in parts.indices) {
                tags.add(parts.take(i + 1).joinToString("/"))
            }
        }
        return tags.sorted()
    }
}
