import { create } from 'zustand';
import type { Note, ClassificationResponse } from '../api';

interface PendingAction {
  id: string;
  type: string;
  description: string;
  data: unknown;
}

interface NoteSelectionContext {
  operation: 'delete' | 'restore';
  message: string;
  candidates: Note[];
}

interface SplitPreview {
  title: string;
  content: string;
  selected: boolean;
}

export type SidebarFilter = 'all' | 'today' | 'starred' | 'ai';

interface UiState {
  // View mode
  viewMode: 'notes' | 'trash';
  setViewMode: (mode: 'notes' | 'trash') => void;

  // Sidebar filter
  sidebarFilter: SidebarFilter;
  setSidebarFilter: (f: SidebarFilter) => void;

  // Selected folder (for NoteListPanel)
  selectedFolderId: string | null;
  setSelectedFolderId: (id: string | null) => void;

  // Status bar
  status: string;
  setStatus: (msg: string) => void;

  // Search
  searchQuery: string;
  searchResults: Note[];
  isSearching: boolean;
  setSearchQuery: (q: string) => void;
  setSearchResults: (results: Note[]) => void;
  setIsSearching: (v: boolean) => void;
  clearSearch: () => void;

  // Modal toggles
  isQuickSwitcherOpen: boolean;
  setIsQuickSwitcherOpen: (v: boolean) => void;
  isTimelineOpen: boolean;
  setIsTimelineOpen: (v: boolean) => void;
  isGraphOpen: boolean;
  setIsGraphOpen: (v: boolean) => void;
  isScheduleFormOpen: boolean;
  setIsScheduleFormOpen: (v: boolean) => void;
  isExtractDialogOpen: boolean;
  setIsExtractDialogOpen: (v: boolean) => void;
  isTracesPanelOpen: boolean;
  setIsTracesPanelOpen: (v: boolean) => void;
  isSettingsOpen: boolean;
  setIsSettingsOpen: (v: boolean) => void;
  isMindMapOpen: boolean;
  setIsMindMapOpen: (v: boolean) => void;
  isAiChatOpen: boolean;
  setIsAiChatOpen: (v: boolean) => void;
  isCostDashboardOpen: boolean;
  setIsCostDashboardOpen: (v: boolean) => void;
  isCanvasOpen: boolean;
  setIsCanvasOpen: (v: boolean) => void;
  isWorkflowsOpen: boolean;
  setIsWorkflowsOpen: (v: boolean) => void;
  isEvalDashOpen: boolean;
  setIsEvalDashOpen: (v: boolean) => void;
  isLinksPanelOpen: boolean;
  setIsLinksPanelOpen: (v: boolean) => void;
  isTaskPanelOpen: boolean;
  setIsTaskPanelOpen: (v: boolean) => void;
  isTaskScheduleOpen: boolean;
  setIsTaskScheduleOpen: (v: boolean) => void;
  isAgentMetricsOpen: boolean;
  setIsAgentMetricsOpen: (v: boolean) => void;

  // Confirm dialog
  pendingActions: PendingAction[];
  showConfirmDialog: boolean;
  setPendingActions: (actions: PendingAction[]) => void;
  setShowConfirmDialog: (v: boolean) => void;

  // Note selection dialog
  showNoteSelectionDialog: boolean;
  noteSelectionContext: NoteSelectionContext | null;
  selectedNoteIds: Set<string>;
  setShowNoteSelectionDialog: (v: boolean) => void;
  setNoteSelectionContext: (ctx: NoteSelectionContext | null) => void;
  setSelectedNoteIds: (ids: Set<string>) => void;
  toggleNoteSelection: (id: string) => void;

  // Classification dialog
  showClassificationDialog: boolean;
  classificationResult: ClassificationResponse | null;
  classificationLoading: boolean;
  selectedClassifications: Set<string>;
  setShowClassificationDialog: (v: boolean) => void;
  setClassificationResult: (r: ClassificationResponse | null) => void;
  setClassificationLoading: (v: boolean) => void;
  setSelectedClassifications: (ids: Set<string>) => void;
  toggleClassification: (id: string) => void;

  // Split dialog
  showSplitDialog: boolean;
  splitPreviews: SplitPreview[];
  splitSourceNote: Note | null;
  deleteAfterSplit: boolean;
  setShowSplitDialog: (v: boolean) => void;
  setSplitPreviews: (p: SplitPreview[]) => void;
  setSplitSourceNote: (n: Note | null) => void;
  setDeleteAfterSplit: (v: boolean) => void;
  toggleSplitItem: (index: number) => void;

  // Export dialog
  showExportDialog: boolean;
  exportCandidates: Note[];
  selectedExportIds: Set<string>;
  setShowExportDialog: (v: boolean) => void;
  setExportCandidates: (notes: Note[]) => void;
  setSelectedExportIds: (ids: Set<string>) => void;
  toggleExportItem: (id: string) => void;

  // Multi-select mode (NoteListPanel batch operations)
  multiSelectMode: boolean;
  multiSelectedNoteIds: Set<string>;
  setMultiSelectMode: (v: boolean) => void;
  toggleMultiSelect: () => void;
  toggleMultiNoteSelection: (id: string) => void;
  selectAllMultiNotes: (noteIds: string[]) => void;
  clearMultiSelection: () => void;

  // Summary dialog
  showSummaryDialog: boolean;
  summaryContent: string;
  summaryTargetNote: Note | null;
  summaryLoading: boolean;
  setShowSummaryDialog: (v: boolean) => void;
  setSummaryContent: (c: string) => void;
  setSummaryTargetNote: (n: Note | null) => void;
  setSummaryLoading: (v: boolean) => void;
}

export const useUiStore = create<UiState>((set) => ({
  viewMode: 'notes',
  setViewMode: (mode) => set({ viewMode: mode }),

  sidebarFilter: 'all',
  setSidebarFilter: (f) => set({ sidebarFilter: f }),

  selectedFolderId: null,
  setSelectedFolderId: (id) => set({ selectedFolderId: id }),

  status: '',
  setStatus: (msg) => set({ status: msg }),

  searchQuery: '',
  searchResults: [],
  isSearching: false,
  setSearchQuery: (q) => set({ searchQuery: q }),
  setSearchResults: (results) => set({ searchResults: results }),
  setIsSearching: (v) => set({ isSearching: v }),
  clearSearch: () => set({ searchQuery: '', searchResults: [], isSearching: false }),

  isQuickSwitcherOpen: false,
  setIsQuickSwitcherOpen: (v) => set({ isQuickSwitcherOpen: v }),
  isTimelineOpen: false,
  setIsTimelineOpen: (v) => set({ isTimelineOpen: v }),
  isGraphOpen: false,
  setIsGraphOpen: (v) => set({ isGraphOpen: v }),
  isScheduleFormOpen: false,
  setIsScheduleFormOpen: (v) => set({ isScheduleFormOpen: v }),
  isExtractDialogOpen: false,
  setIsExtractDialogOpen: (v) => set({ isExtractDialogOpen: v }),
  isTracesPanelOpen: false,
  setIsTracesPanelOpen: (v) => set({ isTracesPanelOpen: v }),
  isSettingsOpen: false,
  setIsSettingsOpen: (v) => set({ isSettingsOpen: v }),
  isMindMapOpen: false,
  setIsMindMapOpen: (v) => set({ isMindMapOpen: v }),
  isAiChatOpen: true,
  setIsAiChatOpen: (v) => set({ isAiChatOpen: v }),
  isCostDashboardOpen: false,
  setIsCostDashboardOpen: (v) => set({ isCostDashboardOpen: v }),
  isCanvasOpen: false,
  setIsCanvasOpen: (v) => set({ isCanvasOpen: v }),
  isWorkflowsOpen: false,
  setIsWorkflowsOpen: (v) => set({ isWorkflowsOpen: v }),
  isEvalDashOpen: false,
  setIsEvalDashOpen: (v) => set({ isEvalDashOpen: v }),
  isLinksPanelOpen: false,
  setIsLinksPanelOpen: (v) => set({ isLinksPanelOpen: v }),
  isTaskPanelOpen: false,
  setIsTaskPanelOpen: (v) => set({ isTaskPanelOpen: v }),
  isTaskScheduleOpen: false,
  setIsTaskScheduleOpen: (v) => set({ isTaskScheduleOpen: v }),
  isAgentMetricsOpen: false,
  setIsAgentMetricsOpen: (v) => set({ isAgentMetricsOpen: v }),

  pendingActions: [],
  showConfirmDialog: false,
  setPendingActions: (actions) => set({ pendingActions: actions }),
  setShowConfirmDialog: (v) => set({ showConfirmDialog: v }),

  showNoteSelectionDialog: false,
  noteSelectionContext: null,
  selectedNoteIds: new Set(),
  setShowNoteSelectionDialog: (v) => set({ showNoteSelectionDialog: v }),
  setNoteSelectionContext: (ctx) => set({ noteSelectionContext: ctx }),
  setSelectedNoteIds: (ids) => set({ selectedNoteIds: ids }),
  toggleNoteSelection: (id) =>
    set((s) => {
      const next = new Set(s.selectedNoteIds);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return { selectedNoteIds: next };
    }),

  showClassificationDialog: false,
  classificationResult: null,
  classificationLoading: false,
  selectedClassifications: new Set(),
  setShowClassificationDialog: (v) => set({ showClassificationDialog: v }),
  setClassificationResult: (r) => set({ classificationResult: r }),
  setClassificationLoading: (v) => set({ classificationLoading: v }),
  setSelectedClassifications: (ids) => set({ selectedClassifications: ids }),
  toggleClassification: (id) =>
    set((s) => {
      const next = new Set(s.selectedClassifications);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return { selectedClassifications: next };
    }),

  showSplitDialog: false,
  splitPreviews: [],
  splitSourceNote: null,
  deleteAfterSplit: false,
  setShowSplitDialog: (v) => set({ showSplitDialog: v }),
  setSplitPreviews: (p) => set({ splitPreviews: p }),
  setSplitSourceNote: (n) => set({ splitSourceNote: n }),
  setDeleteAfterSplit: (v) => set({ deleteAfterSplit: v }),
  toggleSplitItem: (index) =>
    set((s) => ({
      splitPreviews: s.splitPreviews.map((item, i) =>
        i === index ? { ...item, selected: !item.selected } : item
      ),
    })),

  showExportDialog: false,
  exportCandidates: [],
  selectedExportIds: new Set(),
  setShowExportDialog: (v) => set({ showExportDialog: v }),
  setExportCandidates: (notes) => set({ exportCandidates: notes }),
  setSelectedExportIds: (ids) => set({ selectedExportIds: ids }),
  toggleExportItem: (id) =>
    set((s) => {
      const next = new Set(s.selectedExportIds);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return { selectedExportIds: next };
    }),

  multiSelectMode: false,
  multiSelectedNoteIds: new Set<string>(),
  setMultiSelectMode: (v) => set({ multiSelectMode: v, multiSelectedNoteIds: new Set() }),
  toggleMultiSelect: () =>
    set((s) => ({ multiSelectMode: !s.multiSelectMode, multiSelectedNoteIds: new Set() })),
  toggleMultiNoteSelection: (id) =>
    set((s) => {
      const next = new Set(s.multiSelectedNoteIds);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return { multiSelectedNoteIds: next };
    }),
  selectAllMultiNotes: (noteIds) => set({ multiSelectedNoteIds: new Set(noteIds) }),
  clearMultiSelection: () => set({ multiSelectedNoteIds: new Set() }),

  showSummaryDialog: false,
  summaryContent: '',
  summaryTargetNote: null,
  summaryLoading: false,
  setShowSummaryDialog: (v) => set({ showSummaryDialog: v }),
  setSummaryContent: (c) => set({ summaryContent: c }),
  setSummaryTargetNote: (n) => set({ summaryTargetNote: n }),
  setSummaryLoading: (v) => set({ summaryLoading: v }),
}));

export type { PendingAction, NoteSelectionContext, SplitPreview };
