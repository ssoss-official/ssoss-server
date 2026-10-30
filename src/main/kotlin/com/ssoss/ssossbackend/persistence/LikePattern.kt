package com.ssoss.ssossbackend.persistence

object LikePattern {
    const val ESCAPE = "!"

    @JvmStatic
    fun forPartialMatch(keyword: String): String {
        val escaped =
            keyword
                .replace(ESCAPE, ESCAPE + ESCAPE)
                .replace("%", "$ESCAPE%")
                .replace("_", "${ESCAPE}_")
        return "%$escaped%"
    }
}
