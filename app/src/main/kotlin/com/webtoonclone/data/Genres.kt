package com.webtoonclone.data

data class Genre(val name: String, val emoji: String, val tagline: String)

/**
 * Every genre MangaDex defines (its "genre" tag group). Names must match MangaDex exactly,
 * because filters look the tag up by name. The live test checks each one resolves.
 */
val Genres = listOf(
    Genre("Action", "⚔️", "Fights, chases, and big stakes"),
    Genre("Adventure", "🧭", "Journeys into the unknown"),
    Genre("Boys' Love", "💙", "Love stories between men"),
    Genre("Comedy", "😄", "Laughs from the first page"),
    Genre("Crime", "🕵️", "Heists, cases, and the wrong side of the law"),
    Genre("Drama", "🎭", "Stories that stay with you"),
    Genre("Fantasy", "✨", "Magic, dragons, and other worlds"),
    Genre("Girls' Love", "💗", "Love stories between women"),
    Genre("Historical", "🏛️", "Stories from other eras"),
    Genre("Horror", "💀", "Fear, one page at a time"),
    Genre("Isekai", "🌀", "Ordinary people in another world"),
    Genre("Magical Girls", "🌟", "Transformations and true friendship"),
    Genre("Mecha", "🤖", "Giant robots and the pilots inside"),
    Genre("Medical", "🩺", "Doctors, patients, and hard calls"),
    Genre("Mystery", "🔍", "Clues, twists, and secrets"),
    Genre("Philosophical", "💭", "Big questions, quiet answers"),
    Genre("Psychological", "🧠", "Minds under pressure"),
    Genre("Romance", "💕", "Love, crushes, and second chances"),
    Genre("Sci-Fi", "🚀", "Futures worth visiting"),
    Genre("Slice of Life", "☀️", "Everyday moments, told well"),
    Genre("Sports", "🏀", "Rivalries and last-second wins"),
    Genre("Superhero", "🦸", "Powers, capes, and choices"),
    Genre("Thriller", "🔪", "Tension that builds each chapter"),
    Genre("Tragedy", "🥀", "Loss, and what comes after"),
    Genre("Wuxia", "🥋", "Martial heroes of ancient China"),
)
