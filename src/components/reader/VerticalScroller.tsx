"use client";

import { useEffect, useRef, useState } from "react";
import { useReadingProgress } from "@/hooks/useReadingProgress";

interface Props {
  seriesId: string;
  chapterId: string;
  pages: string[];
}

export default function VerticalScroller({ seriesId, chapterId, pages }: Props) {
  const { save } = useReadingProgress(seriesId);
  const [visible, setVisible] = useState<Set<number>>(() => new Set([0, 1, 2]));
  const refs = useRef<(HTMLDivElement | null)[]>([]);

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting) continue;
          const index = Number((entry.target as HTMLElement).dataset.index);
          save(chapterId, index);
          // Load the current page plus the next two.
          setVisible((prev) => {
            const next = new Set(prev);
            for (let i = index; i <= index + 2 && i < pages.length; i++) next.add(i);
            return next;
          });
        }
      },
      { rootMargin: "600px 0px" },
    );
    refs.current.forEach((el) => el && observer.observe(el));
    return () => observer.disconnect();
  }, [chapterId, pages.length, save]);

  return (
    <div className="reader">
      {pages.map((src, i) => (
        <div
          key={src}
          data-index={i}
          ref={(el) => {
            refs.current[i] = el;
          }}
          className={visible.has(i) ? undefined : "placeholder"}
        >
          {/* eslint-disable-next-line @next/next/no-img-element */}
          {visible.has(i) && <img src={src} alt={`Page ${i + 1}`} loading="lazy" />}
        </div>
      ))}
    </div>
  );
}
