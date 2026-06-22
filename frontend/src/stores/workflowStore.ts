import { create } from 'zustand';

// ── Types ──

export type WorkflowItemStatus = 'waiting' | 'executing' | 'success' | 'failed';

export type WorkflowState =
  | 'thinking'
  | 'awaiting_confirmation'
  | 'executing'
  | 'done'
  | 'partial_failed'
  | 'failed'
  | 'cancelled';

export type OperationType =
  | 'delete'
  | 'permanent_delete'
  | 'restore'
  | 'move'
  | 'tag'
  | 'remove_tag'
  | 'archive';

export interface WorkflowParams {
  tagName?: string;
  folderId?: string;
}

export interface WorkflowItem {
  noteId: string;
  title: string;
  folder?: string;
  selected: boolean;
  status: WorkflowItemStatus;
  error?: string;
}

export interface ThinkingStep {
  step: string;
  detail: string;
  done: boolean;
}

export interface WorkflowCard {
  id: string;
  operationType: OperationType;
  operationLabel: string;
  state: WorkflowState;
  items: WorkflowItem[];
  progress: { current: number; total: number };
  thinkingSteps: ThinkingStep[];
  params?: WorkflowParams;
  result?: { successCount: number; failedCount: number };
  createdAt: number;
}

// ── Operation labels ──

const operationLabels: Record<OperationType, string> = {
  delete: '删除笔记',
  permanent_delete: '永久删除',
  restore: '恢复笔记',
  move: '移动笔记',
  tag: '添加标签',
  remove_tag: '移除标签',
  archive: '归档笔记',
};

// ── Store ──

interface WorkflowStoreState {
  cards: Record<string, WorkflowCard>;
  activeCardId: string | null;

  createCard: (
    operationType: OperationType,
    items: Array<{ noteId: string; title: string; folder?: string }>,
    params?: WorkflowParams,
  ) => string;
  setThinkingSteps: (cardId: string, steps: ThinkingStep[]) => void;
  toConfirmation: (cardId: string) => void;
  toggleItem: (cardId: string, noteId: string) => void;
  toggleAll: (cardId: string) => void;
  confirm: (cardId: string) => void;
  cancel: (cardId: string) => void;
  updateItemStatus: (cardId: string, noteId: string, status: WorkflowItemStatus, error?: string) => void;
  updateProgress: (cardId: string, current: number) => void;
  complete: (cardId: string, result: { successCount: number; failedCount: number }) => void;
  retryFailed: (cardId: string) => void;
  getCard: (cardId: string) => WorkflowCard | undefined;
  getSelectedItems: (cardId: string) => WorkflowItem[];
}

let nextId = 0;

export const useWorkflowStore = create<WorkflowStoreState>((set, get) => ({
  cards: {},
  activeCardId: null,

  createCard: (operationType, items, params) => {
    const id = `wf-${++nextId}`;
    const card: WorkflowCard = {
      id,
      operationType,
      operationLabel: operationLabels[operationType],
      state: 'thinking',
      items: items.map((i) => ({ ...i, selected: true, status: 'waiting' })),
      progress: { current: 0, total: items.length },
      thinkingSteps: [],
      params,
      createdAt: Date.now(),
    };
    set((s) => ({ cards: { ...s.cards, [id]: card }, activeCardId: id }));
    return id;
  },

  setThinkingSteps: (cardId, steps) => {
    set((s) => {
      const card = s.cards[cardId];
      if (!card) return s;
      return { cards: { ...s.cards, [cardId]: { ...card, thinkingSteps: steps } } };
    });
  },

  toConfirmation: (cardId) => {
    set((s) => {
      const card = s.cards[cardId];
      if (!card || card.state !== 'thinking') return s;
      return { cards: { ...s.cards, [cardId]: { ...card, state: 'awaiting_confirmation' } } };
    });
  },

  toggleItem: (cardId, noteId) => {
    set((s) => {
      const card = s.cards[cardId];
      if (!card || card.state !== 'awaiting_confirmation') return s;
      const items = card.items.map((i) =>
        i.noteId === noteId ? { ...i, selected: !i.selected } : i,
      );
      return { cards: { ...s.cards, [cardId]: { ...card, items } } };
    });
  },

  toggleAll: (cardId) => {
    set((s) => {
      const card = s.cards[cardId];
      if (!card || card.state !== 'awaiting_confirmation') return s;
      const allSelected = card.items.every((i) => i.selected);
      const items = card.items.map((i) => ({ ...i, selected: !allSelected }));
      return { cards: { ...s.cards, [cardId]: { ...card, items } } };
    });
  },

  confirm: (cardId) => {
    set((s) => {
      const card = s.cards[cardId];
      if (!card || card.state !== 'awaiting_confirmation') return s;
      const selectedItems = card.items.filter((i) => i.selected);
      const items = card.items.map((i) =>
        i.selected ? { ...i, status: 'waiting' as const } : i,
      );
      return {
        cards: {
          ...s.cards,
          [cardId]: {
            ...card,
            state: 'executing',
            items,
            progress: { current: 0, total: selectedItems.length },
          },
        },
      };
    });
  },

  cancel: (cardId) => {
    set((s) => {
      const card = s.cards[cardId];
      if (!card) return s;
      return { cards: { ...s.cards, [cardId]: { ...card, state: 'cancelled' } } };
    });
  },

  updateItemStatus: (cardId, noteId, status, error) => {
    set((s) => {
      const card = s.cards[cardId];
      if (!card) return s;
      const items = card.items.map((i) =>
        i.noteId === noteId ? { ...i, status, error } : i,
      );
      return { cards: { ...s.cards, [cardId]: { ...card, items } } };
    });
  },

  updateProgress: (cardId, current) => {
    set((s) => {
      const card = s.cards[cardId];
      if (!card) return s;
      return {
        cards: {
          ...s.cards,
          [cardId]: { ...card, progress: { ...card.progress, current } },
        },
      };
    });
  },

  complete: (cardId, result) => {
    set((s) => {
      const card = s.cards[cardId];
      if (!card) return s;
      let state: WorkflowState;
      if (result.failedCount === 0) {
        state = 'done';
      } else if (result.successCount === 0) {
        state = 'failed';
      } else {
        state = 'partial_failed';
      }
      return {
        cards: {
          ...s.cards,
          [cardId]: {
            ...card,
            state,
            result,
            progress: { ...card.progress, current: card.progress.total },
          },
        },
      };
    });
  },

  retryFailed: (cardId) => {
    set((s) => {
      const card = s.cards[cardId];
      if (!card || (card.state !== 'partial_failed' && card.state !== 'failed')) return s;
      const items = card.items.map((i) =>
        i.status === 'failed' ? { ...i, status: 'waiting' as const, error: undefined } : i,
      );
      const retryCount = items.filter((i) => i.status === 'waiting').length;
      return {
        cards: {
          ...s.cards,
          [cardId]: {
            ...card,
            state: 'executing',
            items,
            progress: { current: 0, total: retryCount },
            result: undefined,
          },
        },
      };
    });
  },

  getCard: (cardId) => get().cards[cardId],

  getSelectedItems: (cardId) => {
    const card = get().cards[cardId];
    return card ? card.items.filter((i) => i.selected) : [];
  },
}));
