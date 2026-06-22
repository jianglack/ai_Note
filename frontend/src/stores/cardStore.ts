import { create } from 'zustand';
import type { CardPayload, CardState, CardStatus } from '../components/chat/cards/types';

interface CardStoreState {
  cards: Record<string, CardState>;
  createCard: (id: string, payload: CardPayload) => void;
  updateCardStatus: (id: string, status: CardStatus, result?: string) => void;
  getCard: (id: string) => CardState | undefined;
}

export const useCardStore = create<CardStoreState>((set, get) => ({
  cards: {},

  createCard: (id, payload) =>
    set((s) => ({
      cards: { ...s.cards, [id]: { payload, status: 'pending' } },
    })),

  updateCardStatus: (id, status, result) =>
    set((s) => {
      const card = s.cards[id];
      if (!card) return s;
      return {
        cards: { ...s.cards, [id]: { ...card, status, result } },
      };
    }),

  getCard: (id) => get().cards[id],
}));
