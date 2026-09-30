import { mangadex } from "./mangadex";
import type { SourceAdapter } from "./types";

const adapters: Record<string, SourceAdapter> = {
  [mangadex.id]: mangadex,
};

export const defaultSource: SourceAdapter = mangadex;

export function getSource(id: string): SourceAdapter {
  const source = adapters[id];
  if (!source) throw new Error(`Unknown source: ${id}`);
  return source;
}
