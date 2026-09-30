package com.webtoonclone.data

data class SeriesSummary(
    val id: String,
    val title: String,
    val coverUrl: String?,
)

data class SeriesDetail(
    val summary: SeriesSummary,
    val description: String,
    val status: String,
    val tags: List<String>,
)

data class Chapter(
    val id: String,
    val number: String,
    val title: String,
    val publishedAt: String,
)

data class ReadingProgress(
    val chapterId: String,
    val page: Int,
)
