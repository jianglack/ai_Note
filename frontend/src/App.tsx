import { lazy, Suspense, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useShallow } from 'zustand/react/shallow';
import { getSchedules } from './api';
import AiChatPanel from './components/chat/AiChatPanel';
import QuickSwitcher from './components/QuickSwitcher';
import MindMapCanvas, { branchesToFlowData } from './components/MindMapCanvas';
import Sidebar from './components/Sidebar';
import NoteListPanel from './components/NoteListPanel';
import EditorPane from './components/EditorPane';
import AppDialogHost from './components/AppDialogHost';
import ConfirmDialogs from './components/ConfirmDialogs';
import ToastContainer from './components/toast/ToastContainer';
import { useAuthStore } from './stores/authStore';
import { useAiStore } from './stores/aiStore';
import { useUiStore } from './stores/uiStore';
import { useNoteStore } from './stores/noteStore';
import { useScheduleStore } from './stores/scheduleStore';
import { useAiChat } from './hooks/useAiChat';
import { useCardActions } from './hooks/useCardActions';
import { useNotes } from './hooks/useNotes';
import './FileTree.css';
import './styles.css';

const TimelineView = lazy(() => import('./components/TimelineView'));
const GraphView = lazy(() => import('./components/GraphView'));
const ScheduleForm = lazy(() => import('./components/ScheduleForm'));
const ScheduleExtractDialog = lazy(() => import('./components/ScheduleExtractDialog'));
const TracesPanel = lazy(() => import('./components/TracesPanel'));
const SettingsPage = lazy(() => import('./components/SettingsPage'));
const CostDashboard = lazy(() => import('./components/admin/CostDashboard'));
const AgentMetricsDashboard = lazy(() => import('./components/admin/AgentMetricsDashboard'));
const CanvasView = lazy(() => import('./components/features/CanvasView'));
const WorkflowsView = lazy(() => import('./components/features/WorkflowsView'));
const EvalDashView = lazy(() => import('./components/features/EvalDashView'));
const TaskPanelView = lazy(() => import('./components/features/TaskPanelView'));
const TaskScheduleView = lazy(() => import('./components/features/TaskScheduleView'));

export default function App() {
  const logout = useAuthStore((state) => state.logout);
  const navigate = useNavigate();
  const handleLogout = async () => { await logout(); navigate('/login'); };

  // Stores
  const ai = useAiStore(useShallow((state) => ({
    messages: state.messages,
    currentMessage: state.currentMessage,
    isTyping: state.isTyping,
    aiPhase: state.aiPhase,
    aiSteps: state.aiSteps,
  })));
  const ui = useUiStore(useShallow((state) => ({
    status: state.status,
    setStatus: state.setStatus,
    setViewMode: state.setViewMode,
    isQuickSwitcherOpen: state.isQuickSwitcherOpen,
    setIsQuickSwitcherOpen: state.setIsQuickSwitcherOpen,
    isTimelineOpen: state.isTimelineOpen,
    setIsTimelineOpen: state.setIsTimelineOpen,
    isGraphOpen: state.isGraphOpen,
    setIsGraphOpen: state.setIsGraphOpen,
    isScheduleFormOpen: state.isScheduleFormOpen,
    setIsScheduleFormOpen: state.setIsScheduleFormOpen,
    isExtractDialogOpen: state.isExtractDialogOpen,
    setIsExtractDialogOpen: state.setIsExtractDialogOpen,
    isTracesPanelOpen: state.isTracesPanelOpen,
    setIsTracesPanelOpen: state.setIsTracesPanelOpen,
    isSettingsOpen: state.isSettingsOpen,
    isMindMapOpen: state.isMindMapOpen,
    setIsMindMapOpen: state.setIsMindMapOpen,
    isAiChatOpen: state.isAiChatOpen,
    setIsAiChatOpen: state.setIsAiChatOpen,
    isCostDashboardOpen: state.isCostDashboardOpen,
    setIsCostDashboardOpen: state.setIsCostDashboardOpen,
    isCanvasOpen: state.isCanvasOpen,
    setIsCanvasOpen: state.setIsCanvasOpen,
    isWorkflowsOpen: state.isWorkflowsOpen,
    setIsWorkflowsOpen: state.setIsWorkflowsOpen,
    isEvalDashOpen: state.isEvalDashOpen,
    setIsEvalDashOpen: state.setIsEvalDashOpen,
    isTaskPanelOpen: state.isTaskPanelOpen,
    setIsTaskPanelOpen: state.setIsTaskPanelOpen,
    isTaskScheduleOpen: state.isTaskScheduleOpen,
    setIsTaskScheduleOpen: state.setIsTaskScheduleOpen,
    isAgentMetricsOpen: state.isAgentMetricsOpen,
    setIsAgentMetricsOpen: state.setIsAgentMetricsOpen,
  })));
  const { notes, selectedNote } = useNoteStore(useShallow((state) => ({
    notes: state.notes,
    selectedNote: state.selectedNote,
  })));
  const scheduleStore = useScheduleStore(useShallow((state) => ({
    schedules: state.schedules,
    editingSchedule: state.editingSchedule,
    setSchedules: state.setSchedules,
    setEditingSchedule: state.setEditingSchedule,
    addSchedule: state.addSchedule,
    addSchedules: state.addSchedules,
    updateScheduleInList: state.updateScheduleInList,
  })));

  // Hooks
  const { loadChatHistory, handleAiMessage, handleCancelAi, handlePlanAction } = useAiChat();
  const { handleCardAction } = useCardActions();
  const { loadData, handleSelectNote, handleCreateNote } = useNotes();

  // 加载数据
  useEffect(() => {
    const load = async () => {
      try {
        const schedulesData = await getSchedules();
        scheduleStore.setSchedules(schedulesData);
      } catch (err) {
        ui.setStatus((err as Error).message);
      }
      loadData();
    };
    load();
  }, []);

  // 加载 AI 对话历史
  useEffect(() => { loadChatHistory(); }, []);

  // 全局快捷键 Ctrl+P
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'p') {
        e.preventDefault();
        ui.setIsQuickSwitcherOpen(true);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  // 便捷引用
  const { schedules } = scheduleStore;

  return (
    <div className="app">
      <Sidebar onLogout={handleLogout} />
      <NoteListPanel
        notes={notes}
        selectedNoteId={selectedNote?.id}
        onSelectNote={(note) => {
          handleSelectNote(note);
          ui.setViewMode('notes');
        }}
        onCreateNote={() => handleCreateNote()}
      />
      <div style={{ overflow: 'hidden', display: 'flex', minWidth: 0 }}>
        <EditorPane />
        {ui.isAiChatOpen && (
          <AiChatPanel
            messages={ai.messages}
            currentMessage={ai.currentMessage}
            isTyping={ai.isTyping}
            aiPhase={ai.aiPhase}
            aiSteps={ai.aiSteps}
            onSendMessage={handleAiMessage}
            onCancel={handleCancelAi}
            onClose={() => ui.setIsAiChatOpen(false)}
            noteCount={notes.length}
            onCardAction={handleCardAction}
            onPlanAction={handlePlanAction}
            onNoteClick={(noteId) => {
              const note = notes.find(n => n.id === noteId);
              if (note) {
                handleSelectNote(note);
                ui.setViewMode('notes');
              }
            }}
          />
        )}
      </div>
      {ui.status && (
        <div className="col-span-full px-md py-xs text-xs text-text-secondary bg-sidebar border-t border-border truncate">{ui.status}</div>
      )}

      <QuickSwitcher
        isOpen={ui.isQuickSwitcherOpen}
        onClose={() => ui.setIsQuickSwitcherOpen(false)}
        notes={notes}
        onSelectNote={(noteId) => {
          const note = notes.find(n => n.id === noteId);
          if (note) {
            handleSelectNote(note);
            ui.setViewMode('notes');
          }
        }}
      />

      <Suspense fallback={null}>
        {ui.isTimelineOpen && (
          <TimelineView
            notes={notes}
            schedules={schedules}
            onSelectNote={(note) => {
              handleSelectNote(note);
              ui.setViewMode('notes');
            }}
            onSelectSchedule={(schedule) => {
              scheduleStore.setEditingSchedule(schedule);
              ui.setIsScheduleFormOpen(true);
            }}
            onScheduleStatusChange={(updated) => {
              scheduleStore.updateScheduleInList(updated);
            }}
            onClose={() => ui.setIsTimelineOpen(false)}
          />
        )}

      {ui.isGraphOpen && (
        <GraphView
          notes={notes}
          schedules={schedules}
          selectedNoteId={selectedNote?.id}
          onSelectNote={(note) => {
            handleSelectNote(note);
            ui.setViewMode('notes');
          }}
          onSelectSchedule={(schedule) => {
            scheduleStore.setEditingSchedule(schedule);
            ui.setIsScheduleFormOpen(true);
          }}
          onClose={() => ui.setIsGraphOpen(false)}
        />
      )}

      {ui.isScheduleFormOpen && (
        <ScheduleForm
          schedule={scheduleStore.editingSchedule}
          notes={notes}
          onSave={(saved) => {
            if (scheduleStore.editingSchedule) {
              scheduleStore.updateScheduleInList(saved);
            } else {
              scheduleStore.addSchedule(saved);
            }
            ui.setIsScheduleFormOpen(false);
            scheduleStore.setEditingSchedule(undefined);
          }}
          onClose={() => {
            ui.setIsScheduleFormOpen(false);
            scheduleStore.setEditingSchedule(undefined);
          }}
        />
      )}

      {ui.isExtractDialogOpen && selectedNote && (
        <ScheduleExtractDialog
          noteId={selectedNote.id}
          onSave={(createdSchedules) => {
            scheduleStore.addSchedules(createdSchedules);
            ui.setIsExtractDialogOpen(false);
            ui.setStatus(`已创建 ${createdSchedules.length} 个日程`);
          }}
          onClose={() => ui.setIsExtractDialogOpen(false)}
        />
      )}

      {ui.isTracesPanelOpen && (
        <TracesPanel onClose={() => ui.setIsTracesPanelOpen(false)} />
      )}

      {ui.isSettingsOpen && <SettingsPage />}

      {ui.isCostDashboardOpen && (
        <div style={{ position: 'fixed', inset: 0, zIndex: 60, background: '#f9fafb', overflow: 'auto' }}>
          <div style={{ position: 'sticky', top: 0, background: '#fff', borderBottom: '1px solid #e5e7eb', padding: '12px 24px', display: 'flex', justifyContent: 'space-between', alignItems: 'center', zIndex: 1 }}>
            <span style={{ fontWeight: 600 }}>API Cost Dashboard</span>
            <button type="button" aria-label="Close cost dashboard" onClick={() => ui.setIsCostDashboardOpen(false)} style={{ padding: '4px 12px', borderRadius: 6, border: '1px solid #d1d5db', cursor: 'pointer' }}>Close</button>
          </div>
          <CostDashboard />
        </div>
      )}

      {ui.isMindMapOpen && selectedNote && (
        <MindMapCanvas
          title={selectedNote.title || '无标题'}
          initialData={branchesToFlowData(selectedNote.title || '无标题', [
            { label: '主题一', color: '#b8452e', children: ['要点 A', '要点 B', '要点 C'] },
            { label: '主题二', color: '#6b7a5a', children: ['关联 1', '关联 2'] },
            { label: '主题三', color: '#c9a959', children: ['细节 X', '细节 Y', '细节 Z'] },
            { label: '主题四', color: '#8a6bb1', children: ['扩展 α', '扩展 β'] },
          ])}
          editable={true}
          onClose={() => ui.setIsMindMapOpen(false)}
        />
      )}

      {ui.isCanvasOpen && (
        <div style={{ position: 'fixed', inset: 0, zIndex: 60, background: 'var(--color-paper-0, #fbf7ee)' }}>
          <CanvasView onClose={() => ui.setIsCanvasOpen(false)} />
        </div>
      )}

      {ui.isWorkflowsOpen && (
        <div style={{ position: 'fixed', inset: 0, zIndex: 60, background: 'var(--color-paper-0, #fbf7ee)' }}>
          <WorkflowsView onClose={() => ui.setIsWorkflowsOpen(false)} />
        </div>
      )}

      {ui.isEvalDashOpen && (
        <div style={{ position: 'fixed', inset: 0, zIndex: 60, background: 'var(--color-paper-0, #fbf7ee)' }}>
          <EvalDashView onClose={() => ui.setIsEvalDashOpen(false)} />
        </div>
      )}

      {ui.isTaskPanelOpen && (
        <TaskPanelView onClose={() => ui.setIsTaskPanelOpen(false)} />
      )}

      {ui.isTaskScheduleOpen && (
        <TaskScheduleView onClose={() => ui.setIsTaskScheduleOpen(false)} />
      )}

        {ui.isAgentMetricsOpen && (
          <div style={{ position: 'fixed', inset: 0, zIndex: 60, background: 'var(--color-paper-0, #fbf7ee)', overflow: 'auto' }}>
            <AgentMetricsDashboard onClose={() => ui.setIsAgentMetricsOpen(false)} />
          </div>
        )}
      </Suspense>

      <ConfirmDialogs />
      <AppDialogHost />
      <ToastContainer />
    </div>
  );
}
