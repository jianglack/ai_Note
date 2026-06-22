import { create } from 'zustand';
import type { Note, Folder } from '../api';
import { loadNotesAndFolders } from '../services/noteData';

interface NoteState {
  notes: Note[];
  folders: Folder[];
  selectedNote: Note | null;
  trashNotes: Note[];

  // Actions
  setNotes: (notes: Note[]) => void;
  setFolders: (folders: Folder[]) => void;
  setSelectedNote: (note: Note | null) => void;
  setTrashNotes: (notes: Note[]) => void;
  addNote: (note: Note) => void;
  updateNoteInList: (updated: Note) => void;
  updateNoteContent: (id: string, content: string) => void;
  removeNote: (id: string) => void;
  addFolder: (folder: Folder) => void;
  updateFolderInList: (updated: Folder) => void;
  removeFolder: (id: string) => void;
  moveNotesToRoot: (folderId: string) => void;
  removeTrashNote: (id: string) => void;
  loadData: () => Promise<void>;
}

export const useNoteStore = create<NoteState>((set, get) => ({
  notes: [],
  folders: [],
  selectedNote: null,
  trashNotes: [],

  setNotes: (notes) => set({ notes }),
  setFolders: (folders) => set({ folders }),
  setSelectedNote: (note) => set({ selectedNote: note }),
  setTrashNotes: (notes) => set({ trashNotes: notes }),

  addNote: (note) => set((s) => ({ notes: [note, ...s.notes] })),
  updateNoteInList: (updated) =>
    set((s) => ({
      notes: s.notes.map((n) => (n.id === updated.id ? updated : n)),
      selectedNote: s.selectedNote?.id === updated.id ? updated : s.selectedNote,
    })),
  removeNote: (id) =>
    set((s) => ({
      notes: s.notes.filter((n) => n.id !== id),
      selectedNote: s.selectedNote?.id === id ? null : s.selectedNote,
    })),

  addFolder: (folder) => set((s) => ({ folders: [...s.folders, folder] })),
  updateFolderInList: (updated) =>
    set((s) => ({ folders: s.folders.map((f) => (f.id === updated.id ? updated : f)) })),
  removeFolder: (id) =>
    set((s) => ({ folders: s.folders.filter((f) => f.id !== id) })),
  moveNotesToRoot: (folderId) =>
    set((s) => ({
      notes: s.notes.map((n) => (n.folderId === folderId ? { ...n, folderId: null } : n)),
    })),
  removeTrashNote: (id) =>
    set((s) => ({ trashNotes: s.trashNotes.filter((n) => n.id !== id) })),
  
  updateNoteContent: (id, content) =>
    set((s) => ({
      notes: s.notes.map((n) => (n.id === id ? { ...n, content } : n)),
      selectedNote: s.selectedNote?.id === id ? { ...s.selectedNote, content } : s.selectedNote,
    })),
  
  loadData: async () => {
    try {
      const { notes, folders } = await loadNotesAndFolders();
      set({ notes, folders });
    } catch (err) {
      console.error('加载数据失败:', err);
    }
  },
}));
