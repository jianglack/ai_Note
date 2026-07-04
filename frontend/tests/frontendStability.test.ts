import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const root = process.cwd();
const read = (path: string) => readFileSync(join(root, path), 'utf8');

describe('frontend stability guards', () => {
  it('centralizes API base URL and does not log out on 403', () => {
    const apiBase = read('src/services/apiBase.ts');
    const apiService = read('src/services/api.ts');
    const tiptap = read('src/components/TiptapEditor.tsx');
    const useAiChat = read('src/hooks/useAiChat.ts');

    assert.match(apiBase, /VITE_API_BASE_URL/);
    assert.match(apiService, /status === 401/);
    assert.doesNotMatch(apiService, /status === 401\s*\|\|\s*error\.response\?\.status === 403/);
    assert.doesNotMatch(tiptap, /localhost:8081/);
    assert.match(tiptap, /API_BASE_URL/);
    assert.match(useAiChat, /getAuthToken/);
    assert.doesNotMatch(useAiChat, /localStorage\.getItem\('token'\)/);
  });

  it('cleans up eval and workflow polling intervals on unmount', () => {
    const evalDash = read('src/components/features/EvalDashView.tsx');
    const workflows = read('src/components/features/WorkflowsView.tsx');

    assert.match(evalDash, /pollRef/);
    assert.match(evalDash, /clearInterval\(pollRef\.current\)/);
    assert.match(workflows, /pollIntervalsRef/);
    assert.match(workflows, /clearInterval\(interval\)/);
  });

  it('code-splits heavyweight feature panels and vendor bundles', () => {
    const app = read('src/App.tsx');
    const viteConfig = read('vite.config.ts');

    assert.match(app, /lazy\(\(\) => import/);
    assert.match(app, /<Suspense/);
    assert.doesNotMatch(app, /import CanvasView from '\.\/components\/features\/CanvasView'/);
    assert.doesNotMatch(app, /import EvalDashView from '\.\/components\/features\/EvalDashView'/);
    assert.match(viteConfig, /manualChunks/);
  });

  it('uses accessible app dialogs instead of native prompt confirm alert', () => {
    const app = read('src/App.tsx');
    const dialogHost = read('src/components/AppDialogHost.tsx');
    const dialogService = read('src/services/dialogService.ts');

    const checkedFiles = [
      'src/TrashView.tsx',
      'src/components/EditorPane.tsx',
      'src/components/NoteListBatchToolbar.tsx',
      'src/components/TiptapEditor.tsx',
      'src/components/TracesPanel.tsx',
      'src/components/features/CanvasView.tsx',
      'src/components/features/EvalDashView.tsx',
      'src/components/features/WorkflowsView.tsx',
      'src/hooks/useNotes.ts',
    ];

    for (const file of checkedFiles) {
      const content = read(file);
      assert.doesNotMatch(content, /\b(window\.)?(prompt|confirm|alert)\s*\(/, file);
    }

    assert.match(app, /<AppDialogHost \/>/);
    assert.match(dialogHost, /role="dialog"/);
    assert.match(dialogHost, /aria-modal="true"/);
    assert.match(dialogHost, /dialog\.danger \? 'dialog-btn-danger' : 'dialog-btn-confirm'/);
    assert.match(dialogService, /askConfirm/);
    assert.match(dialogService, /askPrompt/);
    assert.match(dialogService, /showAlert/);
  });

  it('stores workflow execution params on workflow cards instead of window globals', () => {
    const workflowHook = read('src/hooks/useWorkflowExecution.ts');
    const workflowStore = read('src/stores/workflowStore.ts');

    assert.doesNotMatch(workflowHook, /__workflowParams/);
    assert.doesNotMatch(workflowHook, /\(window as any\)/);
    assert.match(workflowStore, /interface WorkflowParams/);
    assert.match(workflowStore, /params\?: WorkflowParams/);
    assert.match(workflowHook, /const params = card\.params \|\| \{\}/);
  });

  it('subscribes to Zustand stores with selectors in App', () => {
    const app = read('src/App.tsx');

    assert.doesNotMatch(app, /const\s+\w+\s*=\s*useAiStore\(\s*\)/);
    assert.doesNotMatch(app, /const\s+\w+\s*=\s*useUiStore\(\s*\)/);
    assert.doesNotMatch(app, /const\s+\w+\s*=\s*useNoteStore\(\s*\)/);
    assert.doesNotMatch(app, /const\s+\w+\s*=\s*useScheduleStore\(\s*\)/);
    assert.match(app, /useShallow/);
  });

  it('gives QuickSwitcher dialog and listbox semantics', () => {
    const quickSwitcher = read('src/components/QuickSwitcher.tsx');

    assert.match(quickSwitcher, /role="dialog"/);
    assert.match(quickSwitcher, /aria-modal="true"/);
    assert.match(quickSwitcher, /aria-labelledby=/);
    assert.match(quickSwitcher, /role="listbox"/);
    assert.match(quickSwitcher, /role="option"/);
    assert.match(quickSwitcher, /aria-selected=/);
    assert.match(quickSwitcher, /aria-activedescendant=/);
  });

  it('keeps QuickSwitcher filtering memoized while using refs for stable keyboard navigation', () => {
    const quickSwitcher = read('src/components/QuickSwitcher.tsx');

    assert.match(quickSwitcher, /const filteredNotes = useMemo\(\(\) => notes\.filter/);
    assert.match(quickSwitcher, /filteredNotesRef\.current = filteredNotes/);
    assert.match(quickSwitcher, /selectedIndexRef\.current = selectedIndex/);
    assert.match(quickSwitcher, /filteredNotesRef\.current\[selectedIndexRef\.current\]/);
    assert.match(quickSwitcher, /\}, \[isOpen, onSelectNote, onClose\]\);/);
    assert.doesNotMatch(quickSwitcher, /\}, \[isOpen, selectedIndex, filteredNotes, onSelectNote, onClose\]\);/);
  });

  it('updates TiptapEditor initial HTML when external content changes', () => {
    const tiptap = read('src/components/TiptapEditor.tsx');

    assert.match(tiptap, /const toEditorHtml = \(text: string\): string =>/);
    assert.match(
      tiptap,
      /useEffect\(\(\) => \{\s*setInitialContent\(toEditorHtml\(content\)\);\s*\}, \[content\]\);/
    );
    assert.match(tiptap, /editor\.commands\.setContent\(toEditorHtml\(content\)\)/);
  });

  it('gives chat panel landmark and live-region semantics', () => {
    const aiChatPanel = read('src/components/chat/AiChatPanel.tsx');

    assert.match(aiChatPanel, /role="complementary"/);
    assert.match(aiChatPanel, /aria-label="AI chat assistant"/);
    assert.match(aiChatPanel, /role="log"/);
    assert.match(aiChatPanel, /aria-live="polite"/);
    assert.match(aiChatPanel, /aria-relevant="additions text"/);
    assert.match(aiChatPanel, /role="status"/);
  });

  it('gives NoteListPanel listbox semantics and keyboard note selection', () => {
    const noteListPanel = read('src/components/NoteListPanel.tsx');

    assert.match(noteListPanel, /role="listbox"/);
    assert.match(noteListPanel, /aria-label="Notes"/);
    assert.match(noteListPanel, /role="option"/);
    assert.match(noteListPanel, /aria-selected=/);
    assert.match(noteListPanel, /tabIndex=/);
    assert.match(noteListPanel, /onKeyDown=/);
    assert.match(noteListPanel, /event\.key === 'Enter'/);
    assert.match(noteListPanel, /event\.key === ' '/);
  });

  it('associates LoginPage form labels with inputs', () => {
    const loginPage = read('src/pages/LoginPage.tsx');

    assert.match(loginPage, /useId/);
    assert.match(loginPage, /htmlFor={inputId}/);
    assert.match(loginPage, /id={inputId}/);
    assert.match(loginPage, /type="checkbox"/);
    assert.match(loginPage, /checked={remember}/);
    assert.match(loginPage, /onChange={\(event\) => setRemember\(event\.target\.checked\)}/);
  });

  it('associates ScheduleForm labels with controls', () => {
    const scheduleForm = read('src/components/ScheduleForm.tsx');

    assert.match(scheduleForm, /useId/);
    assert.match(scheduleForm, /const titleId =/);
    assert.match(scheduleForm, /htmlFor={titleId}/);
    assert.match(scheduleForm, /id={titleId}/);
    assert.match(scheduleForm, /htmlFor={startTimeId}/);
    assert.match(scheduleForm, /id={startTimeId}/);
    assert.match(scheduleForm, /type="button"/);
    assert.match(scheduleForm, /aria-expanded={showNoteSelector}/);
    assert.match(scheduleForm, /aria-controls={noteSelectorId}/);
  });

  it('gives ScheduleForm dialog semantics and keyboard focus handling', () => {
    const scheduleForm = read('src/components/ScheduleForm.tsx');

    assert.match(scheduleForm, /role="dialog"/);
    assert.match(scheduleForm, /aria-modal="true"/);
    assert.match(scheduleForm, /aria-labelledby={headingId}/);
    assert.match(scheduleForm, /ref={panelRef}/);
    assert.match(scheduleForm, /onKeyDown={handlePanelKeyDown}/);
    assert.match(scheduleForm, /event\.key === 'Escape'/);
    assert.match(scheduleForm, /event\.key === 'Tab'/);
    assert.match(scheduleForm, /aria-label="Close schedule form"/);
  });

  it('uses native buttons for EmptyState action cards', () => {
    const emptyState = read('src/components/EmptyState.tsx');

    assert.match(emptyState, /function ActionCard/);
    assert.match(emptyState, /type="button"/);
    assert.match(emptyState, /aria-label={ariaLabel}/);
    assert.match(emptyState, /ariaLabel="Create blank note"/);
    assert.match(emptyState, /ariaLabel="Start with AI"/);
    assert.match(emptyState, /ariaLabel="Import notes"/);
    assert.doesNotMatch(emptyState, /<div\s+onClick={onCreateNote}/);
    assert.doesNotMatch(emptyState, /<div\s+onClick={onAiStart}/);
    assert.doesNotMatch(emptyState, /<div\s+onClick={onImport}/);
  });

  it('labels TiptapEditor toolbar and annotation icon buttons', () => {
    const tiptap = read('src/components/TiptapEditor.tsx');

    assert.match(tiptap, /role="toolbar"/);
    assert.match(tiptap, /aria-label="Editor formatting toolbar"/);
    assert.match(tiptap, /aria-label="Toggle bold"/);
    assert.match(tiptap, /aria-label="Insert link"/);
    assert.match(tiptap, /aria-label="Insert image"/);
    assert.match(tiptap, /aria-label="Insert table"/);
    assert.match(tiptap, /aria-label="Add annotation"/);
    assert.match(tiptap, /aria-label="Close annotation dialog"/);
    assert.match(tiptap, /aria-label="Delete annotation"/);
    assert.match(tiptap, /aria-label="Show annotations"/);
  });

  it('labels EditorPane action and paginated toolbar buttons', () => {
    const editorPane = read('src/components/EditorPane.tsx');

    assert.match(editorPane, /aria-label=\{ariaLabel \?\? title\}/);
    assert.match(editorPane, /aria-label="Open AI assistant"/);
    assert.match(editorPane, /aria-label="Editor page toolbar"/);
    assert.match(editorPane, /role="toolbar"/);
    assert.match(editorPane, /aria-label="Toggle bold"/);
    assert.match(editorPane, /aria-label="Insert link"/);
    assert.match(editorPane, /aria-label="Insert image"/);
    assert.match(editorPane, /aria-label="Go to previous page"/);
    assert.match(editorPane, /aria-label="Go to next page"/);
    assert.match(editorPane, /aria-label="Close thumbnails"/);
  });

  it('uses semantic Sidebar controls for navigation and folder actions', () => {
    const sidebar = read('src/components/Sidebar.tsx');

    assert.match(sidebar, /function SidebarRowButton/);
    assert.match(sidebar, /function SidebarIconButton/);
    assert.match(sidebar, /type="button"/);
    assert.match(sidebar, /aria-label="搜索笔记或问 AI"/);
    assert.match(sidebar, /aria-current={active \? 'page' : undefined}/);
    assert.match(sidebar, /aria-label="Create folder"/);
    assert.match(sidebar, /aria-label={`More actions for \$\{folder\.name\}`}/);
    assert.match(sidebar, /aria-label="Open cost dashboard"/);
    assert.match(sidebar, /aria-label="Open settings"/);
    assert.match(sidebar, /role="menu"/);
    assert.match(sidebar, /role="menuitem"/);
    assert.match(sidebar, /handleFolderMenuKeyDown/);
    assert.match(sidebar, /onKeyDown={handleFolderMenuKeyDown}/);
    assert.match(sidebar, /event\.key === 'ArrowDown'/);
    assert.match(sidebar, /event\.key === 'ArrowUp'/);
    assert.match(sidebar, /aria-label={`Set folder color to \$\{c\.name\}`}/);
    assert.match(sidebar, /renameDialogRef/);
    assert.match(sidebar, /renamePreviousFocusRef/);
    assert.match(sidebar, /handleRenameDialogKeyDown/);
    assert.match(sidebar, /role="dialog"/);
    assert.match(sidebar, /aria-modal="true"/);
    assert.match(sidebar, /aria-labelledby="rename-folder-title"/);
    assert.match(sidebar, /id="rename-folder-title"/);
    assert.match(sidebar, /ref={renameDialogRef}/);
    assert.match(sidebar, /onKeyDown={handleRenameDialogKeyDown}/);
    assert.match(sidebar, /renamePreviousFocusRef\.current\?\.focus\(\)/);
    assert.doesNotMatch(sidebar, /<span\s+className="sidebar-folder-more"/);
  });

  it('gives ConfirmDialogs modal semantics and keyboard focus handling', () => {
    const confirmDialogs = read('src/components/ConfirmDialogs.tsx');

    assert.match(confirmDialogs, /dialogPanelRef/);
    assert.match(confirmDialogs, /previousFocusRef/);
    assert.match(confirmDialogs, /previousFocusRef\.current\?\.focus\(\)/);
    assert.match(confirmDialogs, /handleDialogKeyDown/);
    assert.equal((confirmDialogs.match(/role="dialog"/g) ?? []).length, 6);
    assert.equal((confirmDialogs.match(/aria-modal="true"/g) ?? []).length, 6);
    assert.equal((confirmDialogs.match(/aria-labelledby=/g) ?? []).length, 6);
    assert.equal((confirmDialogs.match(/onKeyDown={\(event\) => handleDialogKeyDown/g) ?? []).length, 6);
    assert.equal((confirmDialogs.match(/aria-label="Close /g) ?? []).length, 6);
    assert.doesNotMatch(confirmDialogs, /<button className="dialog-close" onClick=/);
    assert.equal((confirmDialogs.match(/<label\s+key=/g) ?? []).length, 4);
    assert.doesNotMatch(confirmDialogs, /className={`dialog-list-item[^`]+`}\s+onClick=/);
  });

  it('labels feature panel close buttons', () => {
    const closeButtonChecks = [
      ['src/TrashView.tsx', /aria-label="Close trash"/],
      ['src/components/GraphView.tsx', /backLabel="关闭关系图谱"/],
      ['src/components/TimelineView.tsx', /backLabel="关闭时间线"/],
      ['src/components/TracesPanel.tsx', /backLabel="关闭调用追踪"/],
      ['src/components/ScheduleExtractDialog.tsx', /aria-label="Close schedule extraction dialog"/],
      ['src/components/admin/AgentMetricsDashboard.tsx', /backLabel="关闭 Agent 指标"/],
      ['src/components/features/TaskPanelView.tsx', /backLabel="关闭任务中心"/],
      ['src/components/features/TaskScheduleView.tsx', /backLabel="关闭定时任务"/],
      ['src/components/MindMapCanvas.tsx', /aria-label="Close mind map"/],
      ['src/App.tsx', /aria-label="Close cost dashboard"/],
    ] as const;

    for (const [file, pattern] of closeButtonChecks) {
      assert.match(read(file), pattern, file);
    }
  });

  it('migrates second-wave feature tools to the shared workbench shell', () => {
    assert.match(read('src/components/features/CanvasView.tsx'), /ToolWorkbenchShell/);
    assert.match(read('src/components/features/WorkflowsView.tsx'), /ToolWorkbenchShell/);
    assert.match(read('src/components/features/EvalDashView.tsx'), /ToolWorkbenchShell/);
  });

  it('Agent metrics uses the tool workbench shell instead of admin breadcrumbs', () => {
    const metrics = read('src/components/admin/AgentMetricsDashboard.tsx');

    assert.match(metrics, /ToolWorkbenchShell/);
    assert.doesNotMatch(metrics, /amd-breadcrumb/);
  });

  it('task tools use the shared workbench shell', () => {
    assert.match(read('src/components/features/TaskPanelView.tsx'), /ToolWorkbenchShell/);
    assert.match(read('src/components/features/TaskScheduleView.tsx'), /ToolWorkbenchShell/);
  });

  it('uses semantic buttons for feature panel selectable rows', () => {
    const timeline = read('src/components/TimelineView.tsx');
    const traces = read('src/components/TracesPanel.tsx');
    const taskPanel = read('src/components/features/TaskPanelView.tsx');
    const agentMetrics = read('src/components/admin/AgentMetricsDashboard.tsx');

    assert.match(timeline, /<button\s+key={`note-\$\{item\.data\.id\}`}/);
    assert.match(timeline, /aria-label={`Select timeline note \$\{item\.data\.title \|\| 'Untitled'\}`}/);
    assert.doesNotMatch(timeline, /<div\s+key={`note-\$\{item\.data\.id\}`}/);

    assert.match(traces, /<button\s+key={trace\.id}/);
    assert.match(traces, /aria-label={`Select trace \$\{trace\.inputText \|\| trace\.id\}`}/);
    assert.doesNotMatch(traces, /<div\s+key={trace\.id}/);

    assert.match(taskPanel, /<button\s+key={plan\.id}/);
    assert.match(taskPanel, /aria-pressed={isSelected}/);
    assert.doesNotMatch(taskPanel, /<div key={plan\.id} onClick=/);

    assert.match(agentMetrics, /<button\s+type="button"\s+className="amd-detail-head"/);
    assert.match(agentMetrics, /aria-expanded={open}/);
  });
});
