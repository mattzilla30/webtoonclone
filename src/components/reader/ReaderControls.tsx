import Link from "next/link";

interface Props {
  seriesId: string;
  label: string;
  prevId: string | null;
  nextId: string | null;
}

export default function ReaderControls({ seriesId, label, prevId, nextId }: Props) {
  const href = (id: string | null) => (id ? `/series/${seriesId}/${id}` : "#");
  return (
    <nav className="reader-nav">
      <Link href={href(prevId)} className={prevId ? undefined : "disabled"}>← Prev</Link>
      <Link href={`/series/${seriesId}`}>{label}</Link>
      <Link href={href(nextId)} className={nextId ? undefined : "disabled"}>Next →</Link>
    </nav>
  );
}
