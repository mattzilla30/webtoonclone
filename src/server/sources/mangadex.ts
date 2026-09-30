import type {
  ChapterSummary,
  SeriesDetail,
  SeriesSummary,
  SourceAdapter,
} from "./types";

const API = "https://api.mangadex.org";
const PAGE_SIZE = 24;
const LANG = "en";

interface Relationship {
  type: string;
  attributes?: { fileName?: string };
}

interface MangaResource {
  id: string;
  attributes: {
    title: Record<string, string>;
    description: Record<string, string>;
    status: string;
    tags: { attributes: { name: Record<string, string> } }[];
  };
  relationships: Relationship[];
}

interface ChapterResource {
  id: string;
  attributes: {
    chapter: string | null;
    title: string | null;
    publishAt: string;
  };
}

async function get<T>(path: string, params: URLSearchParams = new URLSearchParams()): Promise<T> {
  const res = await fetch(`${API}${path}?${params}`, {
    headers: { "User-Agent": "webtoonclone/0.1" },
    next: { revalidate: 300 },
  });
  if (!res.ok) throw new Error(`MangaDex ${path} failed: ${res.status}`);
  return res.json() as Promise<T>;
}

function firstValue(map: Record<string, string> | undefined): string {
  if (!map) return "";
  return map[LANG] ?? Object.values(map)[0] ?? "";
}

function coverUrl(manga: MangaResource): string | null {
  const cover = manga.relationships.find((r) => r.type === "cover_art");
  const file = cover?.attributes?.fileName;
  return file ? `https://uploads.mangadex.org/covers/${manga.id}/${file}.512.jpg` : null;
}

function toSummary(manga: MangaResource): SeriesSummary {
  return {
    id: manga.id,
    title: firstValue(manga.attributes.title),
    coverUrl: coverUrl(manga),
  };
}

function listParams(page: number): URLSearchParams {
  const p = new URLSearchParams({
    limit: String(PAGE_SIZE),
    offset: String(page * PAGE_SIZE),
    hasAvailableChapters: "true",
  });
  p.append("includes[]", "cover_art");
  p.append("availableTranslatedLanguage[]", LANG);
  p.append("contentRating[]", "safe");
  p.append("contentRating[]", "suggestive");
  return p;
}

export const mangadex: SourceAdapter = {
  id: "mangadex",

  async listPopular(page) {
    const p = listParams(page);
    p.set("order[followedCount]", "desc");
    const { data } = await get<{ data: MangaResource[] }>("/manga", p);
    return data.map(toSummary);
  },

  async search(query, page) {
    const p = listParams(page);
    p.set("title", query);
    const { data } = await get<{ data: MangaResource[] }>("/manga", p);
    return data.map(toSummary);
  },

  async getSeries(id): Promise<SeriesDetail> {
    const p = new URLSearchParams();
    p.append("includes[]", "cover_art");
    const { data } = await get<{ data: MangaResource }>(`/manga/${id}`, p);
    return {
      ...toSummary(data),
      description: firstValue(data.attributes.description),
      status: data.attributes.status,
      tags: data.attributes.tags.map((t) => firstValue(t.attributes.name)),
    };
  },

  async listChapters(seriesId): Promise<ChapterSummary[]> {
    const p = new URLSearchParams({ limit: "500", includeExternalUrl: "0" });
    p.append("translatedLanguage[]", LANG);
    p.append("order[chapter]", "asc");
    const { data } = await get<{ data: ChapterResource[] }>(`/manga/${seriesId}/feed`, p);

    // Several groups often upload the same chapter. Keep the first of each number.
    const seen = new Set<string>();
    const chapters: ChapterSummary[] = [];
    for (const c of data) {
      const number = c.attributes.chapter ?? "Oneshot";
      if (seen.has(number)) continue;
      seen.add(number);
      chapters.push({
        id: c.id,
        number,
        title: c.attributes.title ?? "",
        publishedAt: c.attributes.publishAt,
      });
    }
    return chapters;
  },

  async getPages(chapterId) {
    // At-home URLs expire, so this call must not be cached.
    const res = await fetch(`${API}/at-home/server/${chapterId}`, {
      headers: { "User-Agent": "webtoonclone/0.1" },
      cache: "no-store",
    });
    if (!res.ok) throw new Error(`MangaDex at-home failed: ${res.status}`);
    const json = (await res.json()) as {
      baseUrl: string;
      chapter: { hash: string; data: string[] };
    };
    return json.chapter.data.map((file) => `${json.baseUrl}/data/${json.chapter.hash}/${file}`);
  },
};
