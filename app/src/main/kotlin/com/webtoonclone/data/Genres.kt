package com.webtoonclone.data

data class Genre(val name: String, val tagline: String)

/**
 * Every genre MangaDex defines (its "genre" tag group). Names must match MangaDex exactly,
 * because filters look the tag up by name. The live test checks each one resolves.
 */
val Genres = listOf(
    Genre("Action", "Fights, chases, and big stakes"),
    Genre("Adventure", "Journeys into the unknown"),
    Genre("Boys' Love", "Love stories between men"),
    Genre("Comedy", "Laughs from the first page"),
    Genre("Crime", "Heists, cases, and the wrong side of the law"),
    Genre("Drama", "Stories that stay with you"),
    Genre("Fantasy", "Magic, dragons, and other worlds"),
    Genre("Girls' Love", "Love stories between women"),
    Genre("Historical", "Stories from other eras"),
    Genre("Horror", "Fear, one page at a time"),
    Genre("Isekai", "Ordinary people in another world"),
    Genre("Magical Girls", "Transformations and true friendship"),
    Genre("Mecha", "Giant robots and the pilots inside"),
    Genre("Medical", "Doctors, patients, and hard calls"),
    Genre("Mystery", "Clues, twists, and secrets"),
    Genre("Philosophical", "Big questions, quiet answers"),
    Genre("Psychological", "Minds under pressure"),
    Genre("Romance", "Love, crushes, and second chances"),
    Genre("Sci-Fi", "Futures worth visiting"),
    Genre("Slice of Life", "Everyday moments, told well"),
    Genre("Sports", "Rivalries and last-second wins"),
    Genre("Superhero", "Powers, capes, and choices"),
    Genre("Thriller", "Tension that builds each chapter"),
    Genre("Tragedy", "Loss, and what comes after"),
    Genre("Wuxia", "Martial heroes of ancient China"),
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

