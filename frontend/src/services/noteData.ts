import { getFolders, getNotes } from '../api';

export async function loadNotesAndFolders() {
  const [notes, folders] = await Promise.all([getNotes(), getFolders()]);
  return { notes, folders };
}
