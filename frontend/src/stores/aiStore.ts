import { create } from 'zustand';

interface PendingActionInfo {
  actionType: string;
  actionDescription: string;
  actionJson: string;
  details?: { label: string; warn?: boolean }[];
  irreversible?: boolean;
}

interface AiMessage {
  id: string;
  content: string;
  timestamp: number;
  role: 'spirit' | 'user';
  sources?: Record<number, { id: string; title: string }>;
  degraded?: boolean;
  chatMode?: string;
  degradationReason?: string;
  workflowCardId?: string;
  cardId?: string; // interactive card (multi-select, confirm, schedule, etc.)
  planData?: import('../api').TaskPlanData; // multi-step plan card
  pendingAction?: PendingActionInfo; // single-item PENDING_ACTION confirmation card
}

interface AiStep {
  step: string;
  detail: string;
}

interface AiInlineSuggestion {
  noteId: string;
  noteTitle: string;
  noteDate?: string;
  description: string;
}

interface AiState {
  messages: AiMessage[];
  currentMessage: string;
  isTyping: boolean;
  aiPhase: 'idle' | 'thinking' | 'executing';
  aiSteps: AiStep[];

  // Tag suggestions
  tagSuggestions: string[];
  isLoadingTags: boolean;

  // Inline suggestion
  inlineSuggestion: AiInlineSuggestion | null;

  // Active plans
  activePlans: import('../api').TaskPlanData[];
  setActivePlans: (plans: import('../api').TaskPlanData[]) => void;
  updatePlanInList: (plan: import('../api').TaskPlanData) => void;

  setMessages: (msgs: AiMessage[]) => void;
  addMessage: (msg: AiMessage) => void;
  setCurrentMessage: (msg: string) => void;
  setIsTyping: (v: boolean) => void;
  setAiPhase: (phase: 'idle' | 'thinking' | 'executing') => void;
  setAiSteps: (steps: AiStep[]) => void;
  addAiStep: (step: AiStep) => void;
  setTagSuggestions: (tags: string[]) => void;
  setIsLoadingTags: (v: boolean) => void;
  removeTagSuggestion: (tag: string) => void;
  setInlineSuggestion: (s: AiInlineSuggestion | null) => void;
  resetAiState: () => void;
}

export const useAiStore = create<AiState>((set) => ({
  messages: [],
  currentMessage: '',
  isTyping: false,
  aiPhase: 'idle',
  aiSteps: [],
  tagSuggestions: [],
  isLoadingTags: false,
  inlineSuggestion: null,
  activePlans: [],
  setActivePlans: (plans) => set({ activePlans: plans }),
  updatePlanInList: (plan) => set((s) => ({
    activePlans: s.activePlans.map(p => p.id === plan.id ? plan : p),
    messages: s.messages.map(m => m.planData?.id === plan.id ? { ...m, planData: plan } : m),
  })),

  setMessages: (msgs) => set({ messages: msgs }),
  addMessage: (msg) => set((s) => ({ messages: [...s.messages, msg] })),
  setCurrentMessage: (msg) => set({ currentMessage: msg }),
  setIsTyping: (v) => set({ isTyping: v }),
  setAiPhase: (phase) => set({ aiPhase: phase }),
  setAiSteps: (steps) => set({ aiSteps: steps }),
  addAiStep: (step) =>
    set((s) => {
      if (step.step === 'tool_call' || step.step === 'tool_result') {
        return { aiSteps: [...s.aiSteps, step] };
      }
      const existing = s.aiSteps.findIndex((st) => st.step === step.step);
      if (existing >= 0) {
        return { aiSteps: s.aiSteps.map((st, i) => (i === existing ? step : st)) };
      }
      return { aiSteps: [...s.aiSteps, step] };
    }),
  setTagSuggestions: (tags) => set({ tagSuggestions: tags }),
  setIsLoadingTags: (v) => set({ isLoadingTags: v }),
  removeTagSuggestion: (tag) =>
    set((s) => ({ tagSuggestions: s.tagSuggestions.filter((t) => t !== tag) })),
  setInlineSuggestion: (s) => set({ inlineSuggestion: s }),
  resetAiState: () =>
    set({ currentMessage: '', isTyping: false, aiPhase: 'idle', aiSteps: [] }),
}));

export type { AiMessage, AiStep, AiInlineSuggestion, PendingActionInfo };
