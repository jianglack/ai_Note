import api from './services/api';
import { getAuthToken } from './services/apiBase';
import { parseSseStream } from './services/sse';

export type Tag = {
  id: string;
  name: string;
};

export type Note = {
  id: string;
  title: string;
  content: string;
  folderId?: string | null;
  createdAt: string;
  updatedAt: string;
  deletedAt?: string | null;
  pinned?: boolean;
  starred?: boolean;
  tags: Tag[];
};

export type Folder = {
  id: string;
  name: string;
  parentId?: string | null;
  color?: string | null;
  createdAt: string;
  updatedAt: string;
};

// Note APIs
export async function getNotes(): Promise<Note[]> {
  const res = await api.get('/api/notes');
  return res.data;
}

export async function getNote(id: string): Promise<Note> {
  const res = await api.get(`/api/notes/${id}`);
  return res.data;
}

export async function createNote(payload: { title: string; content: string; tags: string[]; folderId?: string | null }): Promise<Note> {
  const res = await api.post('/api/notes', payload);
  return res.data;
}

export async function updateNote(id: string, payload: { title: string; content: string; tags: string[]; folderId?: string | null; pinned?: boolean; starred?: boolean }): Promise<Note> {
  const res = await api.put(`/api/notes/${id}`, payload);
  return res.data;
}

export async function deleteNote(id: string): Promise<void> {
  await api.delete(`/api/notes/${id}`);
}

export type NoteVersion = {
  id: string;
  noteId: string;
  content: string;
  createdAt: string;
};

export async function getNoteVersions(id: string): Promise<NoteVersion[]> {
  const res = await api.get(`/api/notes/${id}/versions`);
  return res.data;
}

export async function searchNotes(query: string, signal?: AbortSignal): Promise<Note[]> {
  const res = await api.get(`/api/notes/search?q=${encodeURIComponent(query)}`, { signal });
  return res.data;
}

// hybridSearch 使用相同的端点，因为后端的 /api/notes/search 已经实现了混合搜索
export async function hybridSearch(query: string, signal?: AbortSignal): Promise<Note[]> {
  const res = await api.get(`/api/notes/search?q=${encodeURIComponent(query)}`, { signal });
  return res.data;
}

export async function getTrashNotes(): Promise<Note[]> {
  const res = await api.get('/api/notes/trash');
  return res.data;
}

export async function restoreNote(id: string): Promise<void> {
  await api.post(`/api/notes/${id}/restore`);
}

export async function permanentDeleteNote(id: string): Promise<void> {
  await api.delete(`/api/notes/${id}/permanent`);
}

// Folder APIs
export async function getFolders(): Promise<Folder[]> {
  const res = await api.get('/api/folders');
  return res.data;
}

export async function createFolder(name: string, parentId?: string | null): Promise<Folder> {
  const res = await api.post('/api/folders', { name, parentId });
  return res.data;
}

export async function updateFolder(id: string, name: string, color?: string | null, parentId?: string | null): Promise<Folder> {
  const body: Record<string, string | null> = { name, parentId: parentId ?? null };
  if (color !== undefined) body.color = color;
  const res = await api.put(`/api/folders/${id}`, body);
  return res.data;
}

export async function deleteFolder(id: string): Promise<void> {
  await api.delete(`/api/folders/${id}`);
}

// Tag APIs
export async function getTags(): Promise<Tag[]> {
  const res = await api.get('/api/tags');
  return res.data;
}

// AI APIs
export type ChatHistoryMessage = {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  createdAt: string;
};

export type ChatHistoryPage = {
  items: ChatHistoryMessage[];
  nextCursor: string | null;
  hasMore: boolean;
};

export type NoteSource = {
  id: string;
  title: string;
};

export type AiChatResponse = {
  degraded?: boolean;
  chatMode?: string;
  degradationReason?: string;
  content: string;
  sources: Record<number, NoteSource>;  // 笔记编号 -> 笔记信息
  action?: string;  // AI 笔记操作 JSON（可选）
};

// 意图识别响应
export async function getChatHistory(params?: { limit?: number; before?: string | null }): Promise<ChatHistoryPage> {
  const res = await api.get('/api/ai/chat/history', {
    params: {
      limit: params?.limit,
      before: params?.before ?? undefined,
    },
  });
  return res.data;
}

export async function aiChat(message: string, scope: 'all' | 'selected', noteId?: string): Promise<AiChatResponse> {
  const payload: Record<string, unknown> = {
    query: message,
    message: message
  };

  if (scope === 'selected' && noteId) {
    payload.noteIds = [noteId];
  }

  const res = await api.post('/api/ai/chat', payload);
  return res.data;
}

/**
 * 流式 AI 聊天
 * @param message 用户消息
 * @param scope 范围
 * @param noteId 笔记 ID
 * @param onToken 收到新 token 时的回调
 * @param onComplete 完成时的回调
 * @param onError 错误时的回调
 */
export async function aiChatStream(
  message: string,
  scope: 'all' | 'selected',
  noteId: string | undefined,
  onToken: (token: string) => void,
  onComplete: (response: AiChatResponse) => void,
  onError: (error: string) => void,
  onProgress?: (step: string, detail: string) => void,
  signal?: AbortSignal
): Promise<void> {
  const payload: Record<string, unknown> = {
    query: message,
    message: message
  };

  if (scope === 'selected' && noteId) {
    payload.noteIds = [noteId];
  }

  const token = getAuthToken();
  const baseURL = api.defaults.baseURL || '';

  try {
    const response = await fetch(`${baseURL}/api/ai/chat/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': token ? `Bearer ${token}` : '',
        'Accept': 'text/event-stream'
      },
      body: JSON.stringify(payload),
      signal
    });

    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    await parseSseStream(response, ({ event, data }) => {
      try {
        switch (event) {
          case 'token':
            onToken(data);
            break;
          case 'complete':
            onComplete(JSON.parse(data) as AiChatResponse);
            break;
          case 'error':
            onError(data);
            break;
          case 'progress':
            if (onProgress) {
              const p = JSON.parse(data) as { step: string; detail: string };
              onProgress(p.step, p.detail);
            }
            break;
        }
      } catch (e) {
        console.warn(`[SSE] parse error for eventType=${event}:`, e, 'data:', data);
        if (data.trim()) {
          onToken(data);
        }
      }
    }, { defaultEventType: 'token' });
  } catch (error: unknown) {
    onError(error instanceof Error ? error.message : 'Stream connection failed');
  }
}

// RAG 反馈 API
export async function submitRagFeedback(
  query: string, resultNoteId: string, similarityScore: number,
  feedbackType: 'THUMBS_UP' | 'THUMBS_DOWN' | 'CLICK'
): Promise<void> {
  await api.post('/api/ai/rag-feedback', { query, resultNoteId, similarityScore, feedbackType });
}

// Spirit 相关 API
export async function getSpiritGreeting(): Promise<string> {
  const res = await api.get('/api/ai/spirit/greeting');
  return res.data;
}

export async function getSpiritSuggestTags(noteId: string): Promise<string> {
  const res = await api.post('/api/ai/spirit/suggest-tags', { noteId });
  return res.data;
}

// 智能分类相关类型和 API
export type ClassificationSuggestion = {
  noteId: string;
  noteTitle: string;
  suggestedFolderId: string | null;
  suggestedFolderName: string;
  reason: string;
  isNewFolder: boolean;
};

export type ClassificationResponse = {
  suggestions: ClassificationSuggestion[];
  newFolders: string[];
  summary: string;
};

export async function classifyNotes(): Promise<ClassificationResponse> {
  const res = await api.post('/api/ai/classify');
  return res.data;
}

// Annotation type
export type Annotation = {
  id: string;
  noteId: string;
  textContent: string;
  comment: string;
  startOffset: number;
  endOffset: number;
  tags: Tag[];
  createdAt: string;
  updatedAt: string;
};

// Annotation APIs
export async function getAnnotations(noteId: string): Promise<Annotation[]> {
  const res = await api.get(`/api/annotations/note/${noteId}`);
  return res.data;
}

export async function createAnnotation(payload: {
  noteId: string;
  textContent: string;
  comment: string;
  startOffset: number;
  endOffset: number;
  tags: string[];
}): Promise<Annotation> {
  const res = await api.post('/api/annotations', payload);
  return res.data;
}

export async function updateAnnotation(id: string, payload: {
  comment: string;
  tags: string[];
}): Promise<Annotation> {
  const res = await api.put(`/api/annotations/${id}`, payload);
  return res.data;
}

export async function deleteAnnotation(id: string): Promise<void> {
  await api.delete(`/api/annotations/${id}`);
}

// Schedule Types
export type ScheduleStatus = 'pending' | 'in_progress' | 'completed' | 'expired';

export type Schedule = {
  id: string;
  title: string;
  description?: string;
  startTime: string;
  endTime?: string;
  allDay: boolean;
  rrule?: string;
  reminderMinutes?: number;
  status: ScheduleStatus;
  notes: { id: string; title: string }[];
  createdAt: string;
  updatedAt: string;
};

export type ScheduleCreateRequest = {
  title: string;
  description?: string;
  startTime: string;
  endTime?: string;
  allDay?: boolean;
  rrule?: string;
  reminderMinutes?: number;
  noteIds?: string[];
};

// Schedule APIs
export async function getSchedules(startDate?: string, endDate?: string): Promise<Schedule[]> {
  const params = new URLSearchParams();
  if (startDate) params.append('startDate', startDate);
  if (endDate) params.append('endDate', endDate);
  const query = params.toString() ? `?${params.toString()}` : '';
  const res = await api.get(`/api/schedules${query}`);
  return res.data;
}

export async function getSchedule(id: string): Promise<Schedule> {
  const res = await api.get(`/api/schedules/${id}`);
  return res.data;
}

export async function createSchedule(payload: ScheduleCreateRequest): Promise<Schedule> {
  const res = await api.post('/api/schedules', payload);
  return res.data;
}

export async function updateSchedule(id: string, payload: ScheduleCreateRequest): Promise<Schedule> {
  const res = await api.put(`/api/schedules/${id}`, payload);
  return res.data;
}

export async function deleteSchedule(id: string): Promise<void> {
  await api.delete(`/api/schedules/${id}`);
}

export async function updateScheduleStatus(id: string, status: ScheduleStatus): Promise<Schedule> {
  const res = await api.patch(`/api/schedules/${id}/status`, { status });
  return res.data;
}

// AI Extracted Schedule Types
export type ExtractedSchedule = {
  title: string;
  startTime: string;
  endTime?: string;
  allDay: boolean;
  rrule?: string;
  confidence: number;
  source: string;
};

export type ExtractedScheduleResponse = {
  schedules: ExtractedSchedule[];
  noteId: string;
};

// AI Extract Schedules API
export async function extractSchedulesFromNote(noteId: string): Promise<ExtractedScheduleResponse> {
  const res = await api.post('/api/ai/extract-schedules', { noteId });
  return res.data;
}

// Agent Trace APIs
export type AgentTrace = {
  id: string;
  userId: string;
  traceId: string;
  inputText: string;
  outputText: string;
  toolsCalled: string | null;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  latencyMs: number;
  model: string;
  status: string;
  errorMessage: string | null;
  createdAt: string;
};

export type TraceStats = {
  totalCalls: number;
  totalTokens: number;
  todayCalls: number;
  todayTokens: number;
};

export async function getAgentTraces(
  limit: number = 50,
  filters?: { start?: string; end?: string; model?: string }
): Promise<AgentTrace[]> {
  const res = await api.get('/api/ai/traces', { params: { limit, ...filters } });
  return res.data;
}

export async function getTraceStats(): Promise<TraceStats> {
  const res = await api.get('/api/ai/traces/stats');
  return res.data;
}

export async function clearChatMemory(): Promise<void> {
  await api.delete('/api/ai/chat/history');
}

export type MemoryStatus = 'active' | 'disabled' | 'deleted' | 'retracted' | 'superseded';

export type MemoryRecord = {
  id: number;
  type: string;
  memoryType: string | null;
  category: string | null;
  content: string;
  confidence: number | null;
  source: string | null;
  scope: string | null;
  status: MemoryStatus | string;
  sourceTraceId: string | null;
  sourceMessageIds: string | null;
  sourceToolCallId: string | null;
  evidenceExcerpt: string | null;
  lastAccessedAt: string | null;
  accessCount: number | null;
  supersedesId: number | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type MemoryListResponse = {
  items: MemoryRecord[];
  nextCursor: string | null;
};

export type MemoryEventRecord = {
  id: number;
  memoryId: number | null;
  eventType: string;
  actor: string | null;
  reason: string | null;
  beforeJson: string | null;
  afterJson: string | null;
  traceId: string | null;
  createdAt: string | null;
};

export type MemoryEventListResponse = {
  items: MemoryEventRecord[];
};

export type MemoryListParams = {
  type?: string;
  status?: MemoryStatus | string;
  query?: string;
  cursor?: string | null;
};

export type MemoryUpdatePayload = {
  content?: string;
  status?: MemoryStatus;
  memoryType?: string;
  scope?: string;
  confidence?: number;
  reason?: string;
};

export async function getMemories(params: MemoryListParams = {}): Promise<MemoryListResponse> {
  const res = await api.get('/api/memories', {
    params: {
      type: params.type || undefined,
      status: params.status || undefined,
      query: params.query || undefined,
      cursor: params.cursor || undefined,
    },
  });
  return res.data;
}

export async function getMemoryEvents(params: { memoryId?: number; limit?: number } = {}): Promise<MemoryEventListResponse> {
  const res = await api.get('/api/memories/events', {
    params: {
      memoryId: params.memoryId,
      limit: params.limit,
    },
  });
  return res.data;
}

export async function updateMemory(id: number, payload: MemoryUpdatePayload): Promise<MemoryRecord> {
  const res = await api.patch(`/api/memories/${id}`, payload);
  return res.data;
}

export async function deleteMemory(id: number): Promise<void> {
  await api.delete(`/api/memories/${id}`);
}

export async function forgetMemories(payload: { memoryIds?: number[]; query?: string; reason?: string }): Promise<{ deletedCount: number }> {
  const res = await api.post('/api/memories/forget', payload);
  return res.data;
}

export async function exportMemories(): Promise<MemoryListResponse> {
  const res = await api.get('/api/memories/export');
  return res.data;
}

export type ContextPreviewSection = {
  type: string;
  label: string;
  included: boolean;
  estimatedTokens: number;
  content: string;
};

export type ContextPreviewFlowStep = {
  order: number;
  title: string;
  detail: string;
  status: 'active' | 'skipped' | string;
};

export type ContextPreviewResponse = {
  query: string;
  noteIds: string[];
  selectedNoteCount: number;
  intent: string;
  contextChars: number;
  estimatedTokens: number;
  finalContext: string;
  sections: ContextPreviewSection[];
  flow: ContextPreviewFlowStep[];
};

export async function previewContext(payload: { query: string; noteIds?: string[] }): Promise<ContextPreviewResponse> {
  const res = await api.post('/api/ai/context-preview', {
    query: payload.query,
    noteIds: payload.noteIds ?? [],
  });
  return res.data;
}

export async function saveChatMessages(userMessage: string, aiReply: string): Promise<void> {
  await api.post('/api/ai/chat/save', { userMessage, aiReply });
}

// 用户画像类型
export type UserProfile = {
  id: string;
  userId: string;
  interests: string[];
  expertise: string[];
  preferredTools: string[];
  created_at: string;
  updated_at: string;
};

// 用户画像 API
export async function getUserProfile(): Promise<UserProfile> {
  const res = await api.get('/api/user/profile');
  return res.data;
}

export async function updateUserPreferences(preferences: {
  interests: string[];
  expertise: string[];
  preferredTools: string[];
}): Promise<UserProfile> {
  const res = await api.put('/api/user/profile/preferences', preferences);
  return res.data;
}

// 对话状态类型
export type ConversationState = {
  currentTask: string;
  completedSteps: string[];
  pendingActions: string[];
  lastUpdated: string;
};

// 对话状态 API
export async function getConversationState(sessionId: string): Promise<ConversationState> {
  const res = await api.get(`/api/ai/chat/state/${sessionId}`);
  return res.data;
}

export async function updateConversationState(sessionId: string, state: Partial<ConversationState>): Promise<ConversationState> {
  const res = await api.put(`/api/ai/chat/state/${sessionId}`, state);
  return res.data;
}

// Notification APIs
export type NotificationData = {
  id: string;
  type: string;
  title: string;
  content: string;
  source?: string;
  relatedId?: string;
  isRead: boolean;
  createdAt: string;
};

export async function getNotifications(): Promise<NotificationData[]> {
  const res = await api.get('/api/notifications');
  return res.data;
}

export async function getNotificationCount(): Promise<number> {
  const res = await api.get('/api/notifications/count');
  return res.data.count;
}

export async function markNotificationRead(id: string): Promise<void> {
  await api.put(`/api/notifications/${id}/read`);
}

export async function markAllNotificationsRead(): Promise<void> {
  await api.put('/api/notifications/read-all');
}

// Smart Suggestions API
export type SuggestionCard = {
  type: string;
  message: string;
  params: Record<string, string>;
  priority: number;
  card: {
    kind: string;
    suggestionKind?: string;
    text?: string;
    buttons?: Array<{ label: string; variant?: string; action: string }>;
  };
};

export async function getSmartSuggestions(): Promise<SuggestionCard[]> {
  const res = await api.get('/api/ai/suggestions');
  return res.data;
}

// Knowledge Graph APIs
export type KnowledgeGraphNode = {
  id: string;
  label: string;
  type: 'note' | 'folder' | 'tag' | 'schedule' | string;
  refId: string;
};

export type KnowledgeGraphLink = {
  source: string;
  target: string;
  type: string;
  label: string;
};

export type KnowledgeGraphResponse = {
  nodes: KnowledgeGraphNode[];
  links: KnowledgeGraphLink[];
  neo4jEnabled: boolean;
};

export async function getKnowledgeGraph(): Promise<KnowledgeGraphResponse> {
  const res = await api.get('/api/graph');
  return res.data;
}

// ── AI Workflow APIs ──

export async function createWorkflow(name: string, triggerType: string, triggerConfig: string, steps: string): Promise<any> {
  const res = await api.post('/api/workflows', { name, triggerType, triggerConfig, steps });
  return res.data;
}

export async function getWorkflows(): Promise<any[]> {
  const res = await api.get('/api/workflows');
  return res.data;
}

export async function updateWorkflow(id: string, updates: Record<string, string>): Promise<any> {
  const res = await api.put(`/api/workflows/${id}`, updates);
  return res.data;
}

export async function deleteWorkflow(id: string): Promise<void> {
  await api.delete(`/api/workflows/${id}`);
}

export async function toggleWorkflow(id: string): Promise<any> {
  const res = await api.put(`/api/workflows/${id}/toggle`);
  return res.data;
}

export async function runWorkflow(id: string): Promise<{ runId: string; status: string }> {
  const res = await api.post(`/api/workflows/${id}/run`);
  return res.data;
}

export async function getWorkflowRuns(id: string): Promise<any[]> {
  const res = await api.get(`/api/workflows/${id}/runs`);
  return res.data;
}

// ── Link Preview API ──

export async function getLinkPreview(url: string): Promise<{ url: string; title: string; description: string; imageUrl: string; siteName: string; faviconUrl: string }> {
  const res = await api.get('/api/link-preview', { params: { url } });
  return res.data;
}

// ── Note Database APIs ──

export async function createNoteDatabase(noteId: string, name: string, columns: string): Promise<any> {
  const res = await api.post('/api/note-databases', { noteId, name, columns });
  return res.data;
}

export async function getNoteDatabase(id: string): Promise<{ database: any; rows: any[] }> {
  const res = await api.get(`/api/note-databases/${id}`);
  return res.data;
}

export async function getNoteDatabasesByNote(noteId: string): Promise<any[]> {
  const res = await api.get(`/api/note-databases/note/${noteId}`);
  return res.data;
}

export async function updateNoteDatabase(id: string, updates: Record<string, string>): Promise<any> {
  const res = await api.put(`/api/note-databases/${id}`, updates);
  return res.data;
}

export async function addNoteDatabaseRow(dbId: string, data: string): Promise<any> {
  const res = await api.post(`/api/note-databases/${dbId}/rows`, { data });
  return res.data;
}

export async function updateNoteDatabaseRow(dbId: string, rowId: string, data: string): Promise<any> {
  const res = await api.put(`/api/note-databases/${dbId}/rows/${rowId}`, { data });
  return res.data;
}

export async function deleteNoteDatabaseRow(dbId: string, rowId: string): Promise<void> {
  await api.delete(`/api/note-databases/${dbId}/rows/${rowId}`);
}

// ── Canvas APIs ──

export async function createCanvas(title: string, data?: string): Promise<any> {
  const res = await api.post('/api/canvases', { title, data });
  return res.data;
}

export async function getCanvases(): Promise<any[]> {
  const res = await api.get('/api/canvases');
  return res.data;
}

export async function getCanvas(id: string): Promise<any> {
  const res = await api.get(`/api/canvases/${id}`);
  return res.data;
}

export async function updateCanvas(id: string, title?: string, data?: string): Promise<any> {
  const res = await api.put(`/api/canvases/${id}`, { title, data });
  return res.data;
}

export async function deleteCanvas(id: string): Promise<void> {
  await api.delete(`/api/canvases/${id}`);
}

export async function generateCanvasAI(): Promise<{ nodes: any[]; edges: any[]; groups?: any[] }> {
  const res = await api.post('/api/ai/generate-canvas');
  return res.data;
}

// ── Typed Links APIs ──

export async function createTypedLink(sourceNoteId: string, targetNoteId: string, relationType: string, context?: string): Promise<any> {
  const res = await api.post('/api/typed-links', { sourceNoteId, targetNoteId, relationType, context });
  return res.data;
}

export async function getTypedLinksByNote(noteId: string): Promise<any[]> {
  const res = await api.get(`/api/typed-links/note/${noteId}`);
  return res.data;
}

export async function deleteTypedLink(id: string): Promise<void> {
  await api.delete(`/api/typed-links/${id}`);
}

// ── Evaluation APIs ──

export type EvalDataset = {
  id: string;
  name: string;
  description?: string;
  datasetType: string;
  itemCount: number;
  createdAt: string;
  updatedAt: string;
};

export type EvalItem = {
  id: string;
  datasetId: string;
  question: string;
  expectedAnswer?: string;
  expectedToolCalls?: string;
  createdAt: string;
};

export type EvalRun = {
  id: string;
  datasetId: string;
  runType: string;
  status: string;
  summary?: string;
  startedAt: string;
  completedAt?: string;
};

export type EvalResultData = {
  id: string;
  runId: string;
  itemId: string;
  faithfulness?: number;
  answerRelevancy?: number;
  contextPrecision?: number;
  contextRecall?: number;
  taskCompletion?: number;
  toolAccuracy?: number;
  consistency?: number;
  errorRecoveryRate?: number;
  latencyMs?: number;
  totalTokens?: number;
  actualAnswer?: string;
  actualToolCalls?: string;
  evaluationDetails?: string;
};

export async function createEvalDataset(name: string, description: string, datasetType: string): Promise<EvalDataset> {
  const res = await api.post('/api/eval/datasets', { name, description, datasetType });
  return res.data;
}

export async function getEvalDatasets(): Promise<EvalDataset[]> {
  const res = await api.get('/api/eval/datasets');
  return res.data;
}

export async function getEvalDataset(id: string): Promise<{ dataset: EvalDataset; items: EvalItem[] }> {
  const res = await api.get(`/api/eval/datasets/${id}`);
  return res.data;
}

export async function deleteEvalDataset(id: string): Promise<void> {
  await api.delete(`/api/eval/datasets/${id}`);
}

export async function addEvalItem(datasetId: string, question: string, expectedAnswer?: string, expectedToolCalls?: string): Promise<EvalItem> {
  const res = await api.post(`/api/eval/datasets/${datasetId}/items`, { question, expectedAnswer, expectedToolCalls });
  return res.data;
}

export async function deleteEvalItem(itemId: string): Promise<void> {
  await api.delete(`/api/eval/items/${itemId}`);
}

export async function startEvalRun(datasetId: string, runType: string): Promise<{ status: string }> {
  const res = await api.post('/api/eval/run', { datasetId, runType });
  return res.data;
}

export async function getEvalRuns(): Promise<EvalRun[]> {
  const res = await api.get('/api/eval/runs');
  return res.data;
}

export async function getEvalRunDetail(id: string): Promise<{ run: EvalRun; results: EvalResultData[] }> {
  const res = await api.get(`/api/eval/runs/${id}`);
  return res.data;
}

// ── MindMap APIs ──

export type MindMapData = {
  id: string;
  userId: string;
  noteId?: string;
  title: string;
  data: string;
  source: string;
  createdAt: string;
  updatedAt: string;
};

export async function createMindMap(title: string, data: string, noteId?: string, source?: string): Promise<MindMapData> {
  const res = await api.post('/api/mindmaps', { title, data, noteId, source });
  return res.data;
}

export async function getMindMaps(): Promise<MindMapData[]> {
  const res = await api.get('/api/mindmaps');
  return res.data;
}

export async function getMindMap(id: string): Promise<MindMapData> {
  const res = await api.get(`/api/mindmaps/${id}`);
  return res.data;
}

export async function updateMindMap(id: string, title?: string, data?: string): Promise<MindMapData> {
  const res = await api.put(`/api/mindmaps/${id}`, { title, data });
  return res.data;
}

export async function deleteMindMap(id: string): Promise<void> {
  await api.delete(`/api/mindmaps/${id}`);
}

export async function getMindMapsByNote(noteId: string): Promise<MindMapData[]> {
  const res = await api.get(`/api/mindmaps/note/${noteId}`);
  return res.data;
}

// ── Media APIs ──

export async function uploadImage(noteId: string, file: File, alt?: string) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('noteId', noteId);
  if (alt) formData.append('alt', alt);
  const res = await api.post('/api/media/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
  return res.data as { id: string; url: string; filename: string; mimeType: string };
}

export async function saveTable(noteId: string, tableHtml: string, tableJson: string, caption?: string) {
  const res = await api.post('/api/media/table', { noteId, tableHtml, tableJson, caption });
  return res.data as { id: string; tableMarkdown: string; indexed: boolean };
}

export async function getMediaByNote(noteId: string) {
  const res = await api.get(`/api/media/note/${noteId}`);
  return res.data;
}

// ── Batch Operations ──

export type BatchDetail = {
  noteId: string;
  title: string;
  status: 'success' | 'failed';
  error?: string;
};

export type BatchResult = {
  successCount: number;
  failedCount: number;
  details: BatchDetail[];
  failed: Array<{ noteId: string; reason: string }>;
};

export async function batchDeleteNotes(noteIds: string[]): Promise<BatchResult> {
  const res = await api.post('/api/notes/batch/delete', { noteIds });
  return res.data;
}

export async function batchPermanentDeleteNotes(noteIds: string[]): Promise<BatchResult> {
  const res = await api.post('/api/notes/batch/permanent-delete', { noteIds });
  return res.data;
}

export async function batchRestoreNotes(noteIds: string[]): Promise<BatchResult> {
  const res = await api.post('/api/notes/batch/restore', { noteIds });
  return res.data;
}

export async function batchMoveNotes(noteIds: string[], folderId: string): Promise<BatchResult> {
  const res = await api.post('/api/notes/batch/move', { noteIds, folderId });
  return res.data;
}

export async function batchAddTag(noteIds: string[], tagName: string): Promise<BatchResult> {
  const res = await api.post('/api/notes/batch/tag', { noteIds, tagName });
  return res.data;
}

export async function batchRemoveTag(noteIds: string[], tagName: string): Promise<BatchResult> {
  const res = await api.post('/api/notes/batch/remove-tag', { noteIds, tagName });
  return res.data;
}

export async function batchArchiveNotes(noteIds: string[], archive: boolean): Promise<BatchResult> {
  const res = await api.post('/api/notes/batch/archive', { noteIds, archive });
  return res.data;
}

// ── Task Plan APIs ──

export type TaskStepData = {
  id: string;
  order: number;
  action: string;
  description: string;
  status: string;
  inputParams?: string;
  outputResult?: string;
  retryCount: number;
  errorMessage?: string;
};

export type TaskPlanData = {
  id: string;
  goal: string;
  status: string;
  originalQuery: string;
  totalSteps: number;
  completedSteps: number;
  createdAt: string;
  updatedAt: string;
  steps: TaskStepData[];
};

export type TaskRoute = 'DIRECT_AGENT' | 'PLANNED_TASK';

export type TaskRouteDecision = {
  route: TaskRoute;
  confidence: number;
  reason: string;
  requiresUserPlanApproval: boolean;
  estimatedToolSteps: number;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | string;
};

export type SmartChatResponse = {
  type: 'direct' | 'plan_created';
  route?: TaskRoute;
  routeDecision?: TaskRouteDecision;
  content?: string;
  sources?: Record<number, { id: string; title: string }>;
  actionJson?: string;
  plan?: TaskPlanData;
};

export async function routeTask(query: string, noteIds: string[]): Promise<TaskRouteDecision> {
  const res = await api.post('/api/ai/route', { query, noteIds });
  return res.data;
}

export async function smartChat(query: string, noteIds: string[], forcePlan = false): Promise<SmartChatResponse> {
  const res = await api.post('/api/ai/smart-chat', { query, noteIds, forcePlan });
  return res.data;
}

export async function getPlans(): Promise<TaskPlanData[]> {
  const res = await api.get('/api/ai/plans');
  return res.data;
}

export async function getPlanDetail(id: string): Promise<TaskPlanData> {
  const res = await api.get(`/api/ai/plans/${id}`);
  return res.data;
}

export async function approvePlan(id: string): Promise<void> {
  await api.put(`/api/ai/plans/${id}/approve`);
}

export async function pausePlan(id: string): Promise<void> {
  await api.put(`/api/ai/plans/${id}/pause`);
}

export async function resumePlan(id: string): Promise<void> {
  await api.put(`/api/ai/plans/${id}/resume`);
}

export async function cancelPlan(id: string): Promise<void> {
  await api.put(`/api/ai/plans/${id}/cancel`);
}

export async function rollbackPlan(id: string): Promise<void> {
  await api.put(`/api/ai/plans/${id}/rollback`);
}

export async function skipStep(planId: string, stepId: string): Promise<void> {
  await api.put(`/api/ai/plans/${planId}/steps/${stepId}/skip`);
}

export async function modifyStep(planId: string, stepId: string, params: string): Promise<void> {
  await api.put(`/api/ai/plans/${planId}/steps/${stepId}/modify`, { params });
}

/**
 * SSE 订阅计划执行进度
 */
export function subscribePlanProgress(
  planId: string,
  onStepUpdate: (data: { planId: string; stepOrder: number; status: string; description: string }) => void,
  onPlanUpdate: (data: { planId: string; status: string; completedSteps: number; totalSteps: number }) => void,
  onLog?: (data: { planId: string; message: string }) => void
): () => void {
  const token = getAuthToken();
  const baseURL = api.defaults.baseURL || '';
  const controller = new AbortController();

  const fetchSSE = async () => {
    try {
      const response = await fetch(`${baseURL}/api/ai/plans/${planId}/stream`, {
        signal: controller.signal,
        headers: {
          'Authorization': token ? `Bearer ${token}` : '',
          'Accept': 'text/event-stream'
        }
      });

      if (!response.ok) return;

      await parseSseStream(response, ({ event, data }) => {
        try {
          switch (event) {
            case 'step_update': onStepUpdate(JSON.parse(data)); break;
            case 'plan_update': onPlanUpdate(JSON.parse(data)); break;
            case 'log': onLog?.(JSON.parse(data)); break;
          }
        } catch { /* ignore parse errors */ }
      }, { defaultEventType: '' });
    } catch (err) {
      if ((err as DOMException).name !== 'AbortError') {
        // connection closed
      }
    }
  };

  fetchSSE();

  // Return cleanup that aborts the active SSE fetch.
  return () => controller.abort();
}

// ── Task Schedule APIs ──

export type TaskScheduleData = {
  id: string;
  userId: string;
  originalQuery: string;
  triggerType: string;
  scheduledTime?: string;
  enabled: boolean;
  maxRunCount?: number;
  runCount: number;
  lastRunAt?: string;
  nextRunAt?: string;
  lastPlanId?: string;
  createdAt: string;
  updatedAt: string;
};

export async function getTaskSchedules(): Promise<TaskScheduleData[]> {
  const res = await api.get('/api/ai/schedules');
  return res.data;
}

export async function createTaskSchedule(
  query: string, triggerType: string, scheduledTime?: string, maxRunCount?: number
): Promise<TaskScheduleData> {
  const res = await api.post('/api/ai/schedules', { query, triggerType, scheduledTime, maxRunCount });
  return res.data;
}

export async function toggleTaskSchedule(id: string): Promise<{ enabled: boolean }> {
  const res = await api.put(`/api/ai/schedules/${id}/toggle`);
  return res.data;
}

export async function deleteTaskSchedule(id: string): Promise<void> {
  await api.delete(`/api/ai/schedules/${id}`);
}

/* ── PENDING_ACTION feedback (closed-loop AI confirmation) ── */
export async function sendActionFeedback(params: {
  actionJson: string;
  confirmed: boolean;
  feedback?: string;
}): Promise<AiChatResponse> {
  const res = await api.post('/api/ai/action-feedback', params);
  return res.data;
}

/* ── Agent metrics (admin) ── */
export async function getAgentMetrics(): Promise<Record<string, any>> {
  const res = await api.get('/api/admin/agent-metrics');
  return res.data;
}
