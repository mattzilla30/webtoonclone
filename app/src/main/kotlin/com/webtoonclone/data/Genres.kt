package com.webtoonclone.data

data class Genre(val name: String)

/**
 * Every genre MangaDex defines (its "genre" tag group). Names must match MangaDex exactly,
 * because filters look the tag up by name. The live test checks each one resolves.
 */
val Genres = listOf(
    Genre("Action"),
    Genre("Adventure"),
    Genre("Boys' Love"),
    Genre("Comedy"),
    Genre("Crime"),
    Genre("Drama"),
    Genre("Fantasy"),
    Genre("Girls' Love"),
    Genre("Historical"),
    Genre("Horror"),
    Genre("Isekai"),
    Genre("Magical Girls"),
    Genre("Mecha"),
    Genre("Medical"),
    Genre("Mystery"),
    Genre("Philosophical"),
    Genre("Psychological"),
    Genre("Romance"),
    Genre("Sci-Fi"),
    Genre("Slice of Life"),
    Genre("Sports"),
    Genre("Superhero"),
    Genre("Thriller"),
    Genre("Tragedy"),
    Genre("Wuxia"),
)

/** MangaDex "theme" tags. Names must match MangaDex exactly. */
val Themes = listOf(
    "Aliens", "Animals", "Cooking", "Crossdressing", "Delinquents", "Demons", "Genderswap",
    "Ghosts", "Gyaru", "Harem", "Incest", "Loli", "Mafia", "Magic", "Mahjong", "Martial Arts",
    "Military", "Monster Girls", "Monsters", "Music", "Ninja", "Office Workers", "Police",
    "Post-Apocalyptic", "Reincarnation", "Reverse Harem", "Samurai", "School Life", "Shota",
    "Supernatural", "Survival", "Time Travel", "Traditional Games", "Vampires", "Video Games",
    "Villainess", "Virtual Reality", "Zombies",
)

/** MangaDex "format" tags. */
val Formats = listOf(
    "4-Koma", "Adaptation", "Anthology", "Award Winning", "Doujinshi", "Fan Colored",
    "Full Color", "Long Strip", "Official Colored", "Oneshot", "Self-Published", "Web Comic",
)

/** MangaDex "content" tags, which flag mature material. */
val ContentTags = listOf("Gore", "Sexual Violence")

