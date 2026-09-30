import Image from "next/image";
import ChapterList from "@/components/series/ChapterList";
import { defaultSource } from "@/server/sources/registry";

export default async function SeriesPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const [series, chapters] = await Promise.all([
    defaultSource.getSeries(id),
    defaultSource.listChapters(id),
  ]);

  return (
    <main className="container">
      <div style={{ display: "flex", gap: 16 }}>
        {series.coverUrl && (
          <Image src={series.coverUrl} alt={series.title} width={200} height={300} />
        )}
        <div>
          <h1>{series.title}</h1>
          <div className="muted">{series.status}</div>
          <div className="tags">
            {series.tags.map((t) => (
              <span key={t} className="tag">{t}</span>
            ))}
          </div>
          <p>{series.description}</p>
        </div>
      </div>
      <h2>Chapters</h2>
      <ChapterList seriesId={id} chapters={chapters} />
    </main>
  );
}
