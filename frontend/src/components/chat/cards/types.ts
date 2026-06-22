// Card payload types for AI chat interactive cards

export type CardStatus = 'pending' | 'confirmed' | 'cancelled';

export type MultiSelectItem = { id: string; name: string; tag?: string };
export type SingleSelectItem = { id: string; name: string; recommend?: boolean };
export type SuggestionButton = { label: string; variant?: 'primary' | 'subtle'; action: string };

export type CardPayload =
  | {
      kind: 'multi-select';
      title: string;
      meta?: string;
      icon?: string;
      items: MultiSelectItem[];
      defaultSelected?: string[];
      confirmLabel?: string;
      action: string; // backend action type
    }
  | {
      kind: 'single-select';
      title: string;
      meta?: string;
      icon?: string;
      items: SingleSelectItem[];
      defaultSelected?: string;
      action: string;
    }
  | {
      kind: 'text-input';
      title?: string;
      meta?: string;
      initialTitle?: string;
      initialNotebook?: { id: string; name: string };
      initialTags?: string[];
      action: string;
    }
  | {
      kind: 'schedule';
      title?: string;
      meta?: string;
      initial: { title: string; date: string; time: string; remind?: string };
      action: string;
      sourceNoteId?: string;
    }
  | {
      kind: 'suggestion';
      suggestionKind: 'review' | 'expired' | 'merge';
      text: string;
      buttons: SuggestionButton[];
    }
  | {
      kind: 'confirm';
      title: string;
      description: string;
      confirmLabel?: string;
      action: string;
      params?: Record<string, string>;
    }
  | {
      kind: 'tag-picker';
      title: string;
      noteId: string;
      noteName: string;
      tags: string[];
      defaultSelected?: string[];
      action: string;
    };

export interface CardState {
  payload: CardPayload;
  status: CardStatus;
  result?: string; // collapsed summary text after confirm/cancel
}
