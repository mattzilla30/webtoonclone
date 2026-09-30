"use client";

import Link from "next/link";
import { useReadingProgress } from "@/hooks/useReadingProgress";
import type { ChapterSummary } from "@/server/sources/types";

export default function ChapterList({
  seriesId,
  chapters,
}: {
  seriesId: string;
  chapters: ChapterSummary[];
}) {
  const { progress } = useReadingProgress(seriesId);
  const resume = progress ? chapters.find((c) => c.id === progress.chapterId) : null;

  return (
    <>
      {resume && (
        <p>
          <Link href={`/series/${seriesId}/${resume.id}`}>Continue: Chapter {resume.number}</Link>
        </p>
      )}
      <ul className="chapters">
        {[...chapters].reverse().map((c) => (
          <li key={c.id}>
            <Link href={`/series/${seriesId}/${c.id}`}>
              <span>
                Chapter {c.number}
                {c.title && ` · ${c.title}`}
              </span>
              <span className="muted">{new Date(c.publishedAt).toLocaleDateString()}</span>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}
