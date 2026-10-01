package com.dexter.data

import kotlinx.serialization.Serializable

@Serializable
internal data class MangaListDto(val data: List<MangaDto>, val total: Int = 0)

@Serializable
internal data class MangaOneDto(val data: MangaDto)

@Serializable
internal data class MangaDto(
    val id: String,
    val attributes: MangaAttributesDto,
    val relationships: List<RelationshipDto> = emptyList(),
)

@Serializable
internal data class MangaAttributesDto(
    @Serializable(with = LenientStringMap::class) val title: Map<String, String> = emptyMap(),
    val altTitles: List<
        @Serializable(with = LenientStringMap::class)
        Map<String, String>,
    > = emptyList(),
    val originalLanguage: String = "",
    val availableTranslatedLanguages: List<String?> = emptyList(),
    val year: Int? = null,
    val publicationDemographic: String? = null,
    @Serializable(with = LenientStringMap::class) val links: Map<String, String>? = null,
    @Serializable(with = LenientStringMap::class) val description: Map<String, String> = emptyMap(),
    val status: String = "",
    val tags: List<TagDto> = emptyList(),
    /** The id of the newest chapter uploaded in any language. */
    val latestUploadedChapter: String? = null,
)

@Serializable
internal data class ChapterStatsDto(val statistics: Map<String, ChapterStatDto> = emptyMap())

@Serializable
internal data class ChapterStatDto(val comments: CommentsDto? = null)

@Serializable
internal data class CommentsDto(val threadId: Long? = null, val repliesCount: Int? = null)

@Serializable
internal data class AuthorListDto(val data: List<AuthorDto> = emptyList())

@Serializable
internal data class AuthorDto(val id: String, val attributes: AuthorAttributesDto = AuthorAttributesDto())

@Serializable
internal data class AuthorAttributesDto(val name: String = "")

@Serializable
internal data class TagDto(val id: String = "", val attributes: TagAttributesDto)

@Serializable
internal data class TagAttributesDto(
    @Serializable(with = LenientStringMap::class) val name: Map<String, String> = emptyMap(),
    val group: String = "",
)

@Serializable
internal data class TagListDto(val data: List<TagDto>)

@Serializable
internal data class StatsDto(val statistics: Map<String, StatDto> = emptyMap())

@Serializable
internal data class StatDto(val follows: Int? = null, val rating: RatingDto? = null)

@Serializable
internal data class RatingDto(val average: Double? = null, val distribution: Map<String, Int> = emptyMap())

@Serializable
internal data class RelationshipDto(
    val id: String = "",
    val type: String,
    /** For a related series, how it relates: sequel, prequel, spin_off, and so on. */
    val related: String? = null,
    val attributes: RelationshipAttributesDto? = null,
)

@Serializable
internal data class RelationshipAttributesDto(val fileName: String? = null, val name: String? = null)

@Serializable
internal data class ChapterOneDto(val data: ChapterDto)

@Serializable
internal data class ChapterListDto(val data: List<ChapterDto>, val total: Int = 0)

@Serializable
internal data class ChapterDto(
    val id: String,
    val attributes: ChapterAttributesDto,
    val relationships: List<RelationshipDto> = emptyList(),
)

@Serializable
internal data class ChapterAttributesDto(
    val translatedLanguage: String? = null,
    val chapter: String? = null,
    val title: String? = null,
    val publishAt: String = "",
    val volume: String? = null,
    val externalUrl: String? = null,
)

@Serializable
internal data class AtHomeDto(val baseUrl: String, val chapter: AtHomeChapterDto)

@Serializable
internal data class AtHomeChapterDto(
    val hash: String,
    val data: List<String>,
    val dataSaver: List<String> = emptyList(),
)

@Serializable
internal data class CoverListDto(val data: List<CoverDto> = emptyList())

@Serializable
internal data class CoverDto(val attributes: CoverAttributesDto)

@Serializable
internal data class CoverAttributesDto(val fileName: String = "", val volume: String? = null)
