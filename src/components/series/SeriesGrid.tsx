import Image from "next/image";
import Link from "next/link";
import type { SeriesSummary } from "@/server/sources/types";

export default function SeriesGrid({ series }: { series: SeriesSummary[] }) {
  if (series.length === 0) return <p className="muted">No series found.</p>;
  return (
    <div className="grid">
      {series.map((s) => (
        <Link key={s.id} href={`/series/${s.id}`} className="card">
          {s.coverUrl && <Image src={s.coverUrl} alt={s.title} width={256} height={384} />}
          <div className="title">{s.title}</div>
        </Link>
      ))}
    </div>
  );
}
