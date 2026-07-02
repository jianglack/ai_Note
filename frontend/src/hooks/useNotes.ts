import { useCallback, useEffect, useRef } from 'react';
import { useNoteStore } from '../stores/noteStore';
import { useUiStore } from '../stores/uiStore';
import {
  createNote,
  deleteNote,
  createFolder,
  updateFolder,
  deleteFolder,
  getTrashNotes,
  restoreNote,
  permanentDeleteNote,
  hybridSearch,
} from '../api';
import { askConfirm, askPrompt } from '../services/dialogService';
import { loadNotesAndFolders } from '../services/noteData';
import { resolveNoteId as resolveNoteIdFromList } from '../utils/notes';
import type { Note } from '../api';

export function useNotes() {
  const store = useNoteStore();
  const ui = useUiStore();
  const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const searchAbortRef = useRef<AbortController | null>(null);
  const searchSeqRef = useRef(0);

  useEffect(() => () => {
    if (searchTimerRef.current) {
      clearTimeout(searchTimerRef.current);
    }
    searchAbortRef.current?.abort();
  }, []);

  const loadData = useCallback(async () => {
    try {
      const { notes, folders } = await loadNotesAndFolders();
      store.setNotes(notes);
      store.setFolders(folders);
    } catch (err) {
      ui.setStatus((err as Error).message);
    }
  }, [store, ui]);

  const handleSelectNote = useCallback((note: Note) => {
    store.setSelectedNote(note);
  }, [store]);

  const handleCreateNote = useCallback(async (folderId?: string | null) => {
    try {
      const note = await createNote({ title: 'Untitled', content: '', tags: [], folderId });
      store.addNote(note);
      store.setSelectedNote(note);
      ui.setStatus('Note created');
    } catch (err) {
      ui.setStatus((err as Error).message);
    }
  }, [store, ui]);

  const handleDeleteNote = useCallback(async (id: string) => {
    if (!await askConfirm({
      title: 'Delete note',
      message: 'Move this note to trash?',
      confirmLabel: 'Delete',
      danger: true,
    })) return;

    try {
      await deleteNote(id);
      store.removeNote(id);
      ui.setStatus('Note deleted');
    } catch (err) {
      ui.setStatus((err as Error).message);
    }
  }, [store, ui]);

  const handleCreateFolder = useCallback(async (parentId?: string | null) => {
    const name = await askPrompt({
      title: 'New folder',
      message: 'Folder name',
      confirmLabel: 'Create',
    });
    if (!name) return;

    try {
      const folder = await createFolder(name, parentId);
      store.addFolder(folder);
      ui.setStatus('Folder created');
    } catch (err) {
      ui.setStatus((err as Error).message);
    }
  }, [store, ui]);

  const handleDeleteFolder = useCallback(async (id: string) => {
    if (!await askConfirm({
      title: 'Delete folder',
      message: 'Notes in this folder will move to root.',
      confirmLabel: 'Delete',
      danger: true,
    })) return;

    try {
      await deleteFolder(id);
      store.removeFolder(id);
      store.moveNotesToRoot(id);
      ui.setStatus('Folder deleted');
    } catch (err) {
      ui.setStatus((err as Error).message);
    }
  }, [store, ui]);

  const handleRenameFolder = useCallback(async (id: string, name: string) => {
    const folder = store.folders.find(f => f.id === id);
    if (!folder) return;

    try {
      const updated = await updateFolder(id, name, folder.color ?? null, folder.parentId ?? null);
      store.updateFolderInList(updated);
      ui.setStatus('Folder renamed');
    } catch (err) {
      ui.setStatus((err as Error).message);
    }
  }, [store, ui]);

  const handleUpdateFolderColor = useCallback(async (id: string, color: string | null) => {
    const folder = store.folders.find(f => f.id === id);
    if (!folder) return;

    try {
      const updated = await updateFolder(id, folder.name, color, folder.parentId ?? null);
      store.updateFolderInList(updated);
      ui.setStatus(color ? 'Folder color updated' : 'Folder color cleared');
    } catch (err) {
      ui.setStatus((err as Error).message);
    }
  }, [store, ui]);

  const handleSearch = useCallback((query: string) => {
    ui.setSearchQuery(query);
    if (searchTimerRef.current) {
      clearTimeout(searchTimerRef.current);
      searchTimerRef.current = null;
    }
    searchAbortRef.current?.abort();
    const seq = ++searchSeqRef.current;

    if (!query.trim()) {
      ui.clearSearch();
      return;
    }

    ui.setIsSearching(true);
    const controller = new AbortController();
    searchAbortRef.current = controller;

    searchTimerRef.current = setTimeout(async () => {
      try {
        const results = await hybridSearch(query, controller.signal);
        if (controller.signal.aborted || seq !== searchSeqRef.current) return;
        ui.setSearchResults(results);
        ui.setStatus(`Found ${results.length} matching notes`);
      } catch (err) {
        if (controller.signal.aborted || seq !== searchSeqRef.current) return;
        ui.setStatus(`Search failed: ${(err as Error).message}`);
        ui.setSearchResults([]);
      } finally {
        if (seq === searchSeqRef.current) {
          ui.setIsSearching(false);
        }
        if (searchAbortRef.current === controller) {
          searchAbortRef.current = null;
        }
      }
    }, 300);
  }, [ui]);

  const handleOpenTrash = useCallback(async () => {
    try {
      const trash = await getTrashNotes();
      store.setTrashNotes(trash);
      ui.setViewMode('trash');
      ui.setStatus('Trash opened');
    } catch (err) {
      ui.setStatus(`Failed to load trash: ${(err as Error).message}`);
    }
  }, [store, ui]);

  const handleCloseTrash = useCallback(() => {
    ui.setViewMode('notes');
    store.setTrashNotes([]);
  }, [store, ui]);

  const handleRestoreNote = useCallback(async (id: string) => {
    try {
      await restoreNote(id);
      store.removeTrashNote(id);
      const { notes, folders } = await loadNotesAndFolders();
      store.setNotes(notes);
      store.setFolders(folders);
      ui.setStatus('Note restored');
    } catch (err) {
      ui.setStatus(`Restore failed: ${(err as Error).message}`);
    }
  }, [store, ui]);

  const handlePermanentDelete = useCallback(async (id: string) => {
    try {
      await permanentDeleteNote(id);
      store.removeTrashNote(id);
      ui.setStatus('Note permanently deleted');
    } catch (err) {
      ui.setStatus(`Delete failed: ${(err as Error).message}`);
    }
  }, [store, ui]);

  const resolveNoteId = useCallback((noteIdOrTitle: string | undefined): string | undefined => {
    if (!noteIdOrTitle) return undefined;
    return resolveNoteIdFromList(store.notes, noteIdOrTitle);
  }, [store.notes]);

  return {
    loadData,
    handleSelectNote,
    handleCreateNote,
    handleDeleteNote,
    handleCreateFolder,
    handleDeleteFolder,
    handleRenameFolder,
    handleUpdateFolderColor,
    handleSearch,
    handleOpenTrash,
    handleCloseTrash,
    handleRestoreNote,
    handlePermanentDelete,
    resolveNoteId,
  };
}
