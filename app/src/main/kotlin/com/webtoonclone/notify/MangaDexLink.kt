package com.webtoonclone.notify

/** Where a MangaDex web link points. */
sealed interface MangaDexLink {
    data class Title(val id: String) : MangaDexLink

    data class Chapter(val id: String) : MangaDexLink
}

private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
private val HOSTS = setOf("mangadex.org", "www.mangadex.org")

/** Reads `mangadex.org/title/<id>[/slug]` and `mangadex.org/chapter/<id>[/page]`. Anything else is null. */
fun parseMangaDexLink(host: String?, pathSegments: List<String>): MangaDexLink? {
    if (host?.lowercase() !in HOSTS) return null
    val kind = pathSegments.getOrNull(0) ?: return null
    val id = pathSegments.getOrNull(1)?.takeIf { UUID.matches(it) }?.lowercase() ?: return null
    return when (kind) {
        "title", "manga" -> MangaDexLink.Title(id)
        "chapter" -> MangaDexLink.Chapter(id)
        else -> null
    }
}
