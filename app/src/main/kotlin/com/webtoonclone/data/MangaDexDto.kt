package com.webtoonclone.data

import kotlinx.serialization.Serializable

@Serializable
internal data class MangaListDto(val data: List<MangaDto>)

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
    val title: Map<String, String> = emptyMap(),
    val description: Map<String, String> = emptyMap(),
    val status: String = "",
    val tags: List<TagDto> = emptyList(),
)

@Serializable
internal data class TagDto(val attributes: TagAttributesDto)

@Serializable
internal data class TagAttributesDto(val name: Map<String, String> = emptyMap())

@Serializable
internal data class RelationshipDto(
    val type: String,
    val attributes: RelationshipAttributesDto? = null,
)

@Serializable
internal data class RelationshipAttributesDto(val fileName: String? = null)

@Serializable
internal data class ChapterListDto(val data: List<ChapterDto>)

@Serializable
internal data class ChapterDto(val id: String, val attributes: ChapterAttributesDto)

@Serializable
internal data class ChapterAttributesDto(
    val chapter: String? = null,
    val title: String? = null,
    val publishAt: String = "",
)

@Serializable
internal data class AtHomeDto(val baseUrl: String, val chapter: AtHomeChapterDto)

@Serializable
internal data class AtHomeChapterDto(val hash: String, val data: List<String>)
