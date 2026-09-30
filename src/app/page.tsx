import SeriesGrid from "@/components/series/SeriesGrid";
import { defaultSource } from "@/server/sources/registry";

export default async function Home() {
  const series = await defaultSource.listPopular(0);
  return (
    <main className="container">
      <h1>Popular</h1>
      <SeriesGrid series={series} />
    </main>
  );
}
