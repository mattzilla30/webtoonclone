package com.webtoonclone.data

data class Language(val code: String, val name: String)

/** Languages MangaDex chapters come in, using its codes. English is the default. */
val Languages = listOf(
    Language("en", "English"),
    Language("es", "Spanish"),
    Language("es-la", "Spanish (Latin America)"),
    Language("fr", "French"),
    Language("pt-br", "Portuguese (Brazil)"),
    Language("pt", "Portuguese"),
    Language("de", "German"),
    Language("it", "Italian"),
    Language("ru", "Russian"),
    Language("uk", "Ukrainian"),
    Language("pl", "Polish"),
    Language("nl", "Dutch"),
    Language("tr", "Turkish"),
    Language("id", "Indonesian"),
    Language("vi", "Vietnamese"),
    Language("th", "Thai"),
    Language("ar", "Arabic"),
    Language("hu", "Hungarian"),
    Language("zh", "Chinese (Simplified)"),
    Language("zh-hk", "Chinese (Traditional)"),
    Language("ja", "Japanese"),
    Language("ko", "Korean"),
)
