import { useCallback, useEffect, useRef, useState } from 'react';
import { useAiStore } from '../stores/aiStore';
import { useNoteStore } from '../stores/noteStore';
import { useUiStore } from '../stores/uiStore';
import { getSpiritSuggestTags, updateNote } from '../api';

function noteSignature(title: string, content: string, tags: string[]) {
  return `${title}::${content}::${tags.join('|')}`;
}

function tagNamesFromNote(note: NonNullable<ReturnType<typeof useNoteStore.getState>['selectedNote']>) {
  return note.tags?.map((tag) => tag.name) || [];
}

export function useNoteEditor() {
  const store = useNoteStore();
  const ui = useUiStore();
  const ai = useAiStore();
  const selectedNoteId = store.selectedNote?.id;

  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [tags, setTags] = useState<string[]>([]);
  const [tagInput, setTagInput] = useState('');
  const lastSavedRef = useRef('');
  const saveTimerRef = useRef<ReturnType<typeof window.setTimeout> | null>(null);
  const saveSequenceRef = useRef(0);

  useEffect(() => {
    const note = useNoteStore.getState().selectedNote;
    if (!note) {
      setTitle('');
      setContent('');
      setTags([]);
      setTagInput('');
      lastSavedRef.current = '';
      return;
    }

    const nextTags = tagNamesFromNote(note);
    setTitle(note.title);
    setContent(note.content);
    setTags(nextTags);
    setTagInput('');
    lastSavedRef.current = noteSignature(note.title, note.content, nextTags);
  }, [selectedNoteId]);

  useEffect(() => {
    const note = useNoteStore.getState().selectedNote;
    if (!note) return;

    const signature = noteSignature(title, content, tags);
    if (signature === lastSavedRef.current) return;

    if (saveTimerRef.current) {
      window.clearTimeout(saveTimerRef.current);
    }

    const sequence = ++saveSequenceRef.current;
    const noteId = note.id;
    const folderId = note.folderId;
    const nextTags = [...tags];

    saveTimerRef.current = window.setTimeout(async () => {
      try {
        const updated = await updateNote(noteId, {
          title,
          content,
          tags: nextTags,
          folderId,
        });

        if (sequence !== saveSequenceRef.current) return;

        const current = useNoteStore.getState();
        if (current.selectedNote?.id === updated.id) {
          useNoteStore.setState((state) => ({
            notes: state.notes.map((candidate) => (
              candidate.id === updated.id ? updated : candidate
            )),
          }));
        } else {
          current.updateNoteInList(updated);
        }

        lastSavedRef.current = signature;
        ui.setStatus('Autosaved');
      } catch (err) {
        if (sequence === saveSequenceRef.current) {
          ui.setStatus((err as Error).message);
        }
      }
    }, 800);

    return () => {
      if (saveTimerRef.current) {
        window.clearTimeout(saveTimerRef.current);
      }
    };
  }, [title, content, tags, selectedNoteId, ui]);

  const handleAddTag = useCallback(() => {
    const nextTag = tagInput.trim();
    if (!nextTag || tags.includes(nextTag)) return;
    setTags((prev) => [...prev, nextTag]);
    setTagInput('');
  }, [tagInput, tags]);

  const handleRemoveTag = useCallback((tag: string) => {
    setTags((prev) => prev.filter((candidate) => candidate !== tag));
  }, []);

  const handleSuggestTags = useCallback(async () => {
    const note = useNoteStore.getState().selectedNote;
    if (!note) {
      ui.setStatus('Select a note first');
      return;
    }
    if (!note.title && !note.content) {
      ui.setStatus('Cannot suggest tags for an empty note');
      return;
    }

    ai.setIsLoadingTags(true);
    ui.setStatus('AI is analyzing note content...');
    try {
      const tagsString = await getSpiritSuggestTags(note.id);
      const suggested = tagsString.split(',').map((tag) => tag.trim()).filter(Boolean);
      ai.setTagSuggestions(suggested);
      ui.setStatus(`AI suggested ${suggested.length} tags`);
      window.setTimeout(() => ui.setStatus(''), 3000);
    } catch (err) {
      ui.setStatus(`Failed to suggest tags: ${(err as Error).message}`);
    } finally {
      ai.setIsLoadingTags(false);
    }
  }, [ai, ui]);

  const handleAddSuggestedTag = useCallback((tag: string) => {
    if (tags.includes(tag)) {
      ui.setStatus('Tag already exists');
      return;
    }
    setTags((prev) => [...prev, tag]);
    ai.removeTagSuggestion(tag);
  }, [ai, tags, ui]);

  const handleExportMarkdown = useCallback(() => {
    if (!store.selectedNote) return;
    const blob = new Blob([`# ${title}\n\n${content}`], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${title || 'Untitled'}.md`;
    a.click();
    URL.revokeObjectURL(url);
    ui.setStatus('Markdown exported');
  }, [content, store.selectedNote, title, ui]);

  const handleTogglePin = useCallback(async () => {
    const note = useNoteStore.getState().selectedNote;
    if (!note) return;

    try {
      const updated = await updateNote(note.id, {
        title,
        content,
        tags,
        folderId: note.folderId,
        pinned: !note.pinned,
      });
      useNoteStore.getState().updateNoteInList(updated);
      ui.setStatus(updated.pinned ? 'Pinned' : 'Unpinned');
    } catch (err) {
      ui.setStatus((err as Error).message);
    }
  }, [content, tags, title, ui]);

  const handleToggleStar = useCallback(async () => {
    const note = useNoteStore.getState().selectedNote;
    if (!note) return;

    try {
      const updated = await updateNote(note.id, {
        title,
        content,
        tags,
        folderId: note.folderId,
        starred: !note.starred,
      });
      useNoteStore.getState().updateNoteInList(updated);
      ui.setStatus(updated.starred ? 'Starred' : 'Unstarred');
    } catch (err) {
      ui.setStatus((err as Error).message);
    }
  }, [content, tags, title, ui]);

  return {
    title,
    setTitle,
    content,
    setContent,
    tags,
    setTags,
    tagInput,
    setTagInput,
    handleAddTag,
    handleRemoveTag,
    handleSuggestTags,
    handleAddSuggestedTag,
    handleExportMarkdown,
    handleTogglePin,
    handleToggleStar,
  };
}
