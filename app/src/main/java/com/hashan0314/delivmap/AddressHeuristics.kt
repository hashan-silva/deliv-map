package com.hashan0314.delivmap

object AddressHeuristics {
    private val streetRegex = Regex(
        pattern = "(?i)\\b(road|rd\\.?|street|st\\.?|avenue|ave\\.?|lane|ln\\.?|drive|dr\\.?|boulevard|blvd\\.?|way|trail|court|ct\\.?|place|pl\\.?|gatan|gata|väg|vägen)\\b"
    )
    private val postalCodeRegex = Regex("\\b\\d{3}\\s?\\d{2}\\b")

    fun extractCandidates(text: String): List<String> {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) return emptyList()

        val candidates = linkedSetOf<String>()

        for (index in lines.indices) {
            val line = lines[index]
            val nextLine = lines.getOrNull(index + 1)

            val looksLikeStreet = streetRegex.containsMatchIn(line) && line.any { it.isDigit() }
            val containsPostal = postalCodeRegex.containsMatchIn(line)

            if (looksLikeStreet) {
                val builder = StringBuilder(line)
                if (nextLine != null && (postalCodeRegex.containsMatchIn(nextLine) || looksLikeCity(nextLine))) {
                    builder.append(", ").append(nextLine)
                }
                candidates.add(builder.toString())
            } else if (containsPostal) {
                val previous = lines.getOrNull(index - 1)
                if (previous != null && previous.any { it.isLetter() }) {
                    candidates.add("$previous, $line")
                } else {
                    candidates.add(line)
                }
            }
        }

        val fallback = Regex("(?i)(\\d+\\s+[^,\\n]+?(road|street|avenue|lane|drive|boulevard|way|gatan|gata|väg|vägen))")
        fallback.findAll(text).forEach { matchResult ->
            val raw = matchResult.value.trim()
            if (raw.isNotEmpty()) {
                candidates.add(raw)
            }
        }

        return candidates.toList()
    }

    private fun looksLikeCity(line: String): Boolean {
        if (line.length < 3) return false
        val uppercaseRatio = line.count { it.isUpperCase() }.toDouble() / line.length
        return uppercaseRatio > 0.4 || line.contains("city", ignoreCase = true)
    }
}
