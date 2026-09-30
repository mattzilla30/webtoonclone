import { notFound } from "next/navigation";
import ReaderControls from "@/components/reader/ReaderControls";
import VerticalScroller from "@/components/reader/VerticalScroller";
import { defaultSource } from "@/server/sources/registry";

export default async function ReaderPage({
  params,
}: {
  params: Promise<{ id: string; chapter: string }>;
}) {
  const { id, chapter } = await params;
  const [chapters, pages] = await Promise.all([
    defaultSource.listChapters(id),
    defaultSource.getPages(chapter),
  ]);

  const index = chapters.findIndex((c) => c.id === chapter);
  if (index === -1) notFound();

  return (
    <>
      <ReaderControls
        seriesId={id}
        label={`Chapter ${chapters[index].number}`}
        prevId={chapters[index - 1]?.id ?? null}
        nextId={chapters[index + 1]?.id ?? null}
      />
      <VerticalScroller seriesId={id} chapterId={chapter} pages={pages} />
    </>
  );
}
