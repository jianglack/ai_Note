import type { Note } from '../api';

export function resolveNoteId(notes: Note[], noteIdOrTitle: string | undefined): string | undefined {
  if (!noteIdOrTitle) return undefined;
  if (notes.some((note) => note.id === noteIdOrTitle)) return noteIdOrTitle;
  return notes.find((note) => note.title === noteIdOrTitle)?.id;
}
