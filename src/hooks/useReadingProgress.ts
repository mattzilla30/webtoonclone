"use client";

import { useCallback, useEffect, useState } from "react";

const KEY = "reading-progress";

type ProgressMap = Record<string, { chapterId: string; page: number }>;

function load(): ProgressMap {
  try {
    return JSON.parse(localStorage.getItem(KEY) ?? "{}") as ProgressMap;
  } catch {
    return {};
  }
}

export function useReadingProgress(seriesId: string) {
  const [progress, setProgress] = useState<ProgressMap[string] | null>(null);

  useEffect(() => {
    setProgress(load()[seriesId] ?? null);
  }, [seriesId]);

  const save = useCallback(
    (chapterId: string, page: number) => {
      const all = load();
      all[seriesId] = { chapterId, page };
      try {
        localStorage.setItem(KEY, JSON.stringify(all));
      } catch {
        // Storage full or blocked. Progress is a convenience, so skip it.
      }
    },
    [seriesId],
  );

  return { progress, save };
}
