package com.tishan.app.domain.library

enum class LibraryType(val externalValue: String) {
    QUESTION_BANK("题库"),
    EXAM_PAPER("试卷"),
    ;

    companion object {
        fun fromExternalValue(value: String): LibraryType? =
            entries.firstOrNull { it.externalValue == value }
    }
}
