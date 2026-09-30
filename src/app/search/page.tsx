import SeriesGrid from "@/components/series/SeriesGrid";
import { defaultSource } from "@/server/sources/registry";

export default async function SearchPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string }>;
}) {
  const { q = "" } = await searchParams;
  const series = q ? await defaultSource.search(q, 0) : [];
  return (
    <main className="container">
      <h1>{q ? `Results for "${q}"` : "Search"}</h1>
      <SeriesGrid series={series} />
    </main>
  );
}
