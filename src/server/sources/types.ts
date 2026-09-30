export interface SeriesSummary {
  id: string;
  title: string;
  coverUrl: string | null;
}

export interface SeriesDetail extends SeriesSummary {
  description: string;
  status: string;
  tags: string[];
}

export interface ChapterSummary {
  id: string;
  number: string;
  title: string;
  publishedAt: string;
}

export interface SourceAdapter {
  id: string;
  listPopular(page: number): Promise<SeriesSummary[]>;
  search(query: string, page: number): Promise<SeriesSummary[]>;
  getSeries(id: string): Promise<SeriesDetail>;
  listChapters(seriesId: string): Promise<ChapterSummary[]>;
  getPages(chapterId: string): Promise<string[]>;
}
