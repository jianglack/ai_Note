import { useEffect, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import AiWorkflowCard from '../workflow/AiWorkflowCard';
import { useWorkflowStore } from '../../stores/workflowStore';
import { useWorkflowExecution } from '../../hooks/useWorkflowExecution';
import { useCardStore } from '../../stores/cardStore';
import AiCardRenderer from './cards/AiCardRenderer';
import NotificationPanel from './NotificationPanel';
import PlanApprovalCard from './PlanApprovalCard';
import PendingActionCard from './cards/PendingActionCard';
import ChatInput from './ChatInput';
import { markdownUrlTransform, sanitizeAnchorUrl } from '../../security/contentSafety';

interface NoteSource { id: string; title: string; }
interface Message {
  id: string; content: string; timestamp: number;
  role: 'spirit' | 'user'; sources?: Record<number, NoteSource>;
  degraded?: boolean;
  chatMode?: string;
  degradationReason?: string;
  workflowCardId?: string;
  cardId?: string;
  planData?: import('../../api').TaskPlanData;
  pendingAction?: import('../../stores/aiStore').PendingActionInfo;
}
interface AiStep { step: string; detail: string; }

interface AiChatPanelProps {
  messages: Message[]; currentMessage: string; isTyping: boolean;
  aiPhase: 'idle' | 'thinking' | 'executing'; aiSteps?: AiStep[];
  onSendMessage: (message: string) => void; onCancel: () => void;
  onClose: () => void; onNoteClick?: (noteId: string) => void; noteCount?: number;
  onCardAction?: (cardId: string, action: string, data?: unknown) => void;
  onPlanAction?: (action: string, planId: string, stepId?: string) => void;
}

const T = {
  bg: 'var(--color-paper-0, #fbf7ee)',
  surface: 'var(--color-paper-1, #f5efe0)',
  surfaceHover: 'var(--color-paper-2, #efe8d6)',
  border: 'rgba(74,64,50,0.12)',
  borderStrong: 'rgba(74,64,50,0.22)',
  text: 'var(--color-text, #2b2620)',
  textSecondary: 'var(--color-text-secondary, #7a6b58)',
  textTertiary: '#a09888',
  accent: 'var(--color-accent, #b8452e)',
  success: '#6b7a5a',
  font: '-apple-system, "PingFang SC", "Noto Sans SC", "Helvetica Neue", sans-serif',
};

function MoziAvatar({ size = 30 }: { size?: number }) {
  return (
    <div style={{
      width: size, height: size, borderRadius: '50%', flexShrink: 0,
      background: 'linear-gradient(135deg, var(--color-accent, #b8452e), #d96b52)',
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      color: '#fff', fontSize: size * 0.45, fontWeight: 700,
    }}>
      墨
    </div>
  );
}

function preprocessContent(content: string) {
  return content.replace(/\[笔记(\d+)\]/g, '[笔记$1](#spirit-note-$1)');
}

export default function AiChatPanel({
  messages, currentMessage, isTyping, aiPhase, aiSteps = [],
  onSendMessage, onCancel, onClose, onNoteClick, noteCount = 0,
  onCardAction, onPlanAction,
}: AiChatPanelProps) {
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const cards = useWorkflowStore((s) => s.cards);
  const aiCards = useCardStore((s) => s.cards);
  const { executeWorkflowCard, retryWorkflowCard } = useWorkflowExecution();
  const [showNotifications, setShowNotifications] = useState(false);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, currentMessage, cards]);

  const makeComponents = (sources?: Record<number, NoteSource>) => ({
    a: ({ href, children }: { href?: string; children?: React.ReactNode }) => {
      const match = href?.match(/^#spirit-note-(\d+)$/);
      if (match && sources) {
        const source = sources[parseInt(match[1])];
        if (source && onNoteClick) {
          return (
            <span
              style={{ color: T.accent, cursor: 'pointer', textDecoration: 'underline' }}
              onClick={() => onNoteClick(source.id)}
              title={`查看：${source.title}`}
            >{children}</span>
          );
        }
      }
      const safeHref = sanitizeAnchorUrl(href);
      if (!safeHref) {
        return <span style={{ color: T.accent }}>{children}</span>;
      }
      return <a href={safeHref} target="_blank" rel="noopener noreferrer" style={{ color: T.accent }}>{children}</a>;
    },
  });

  const isEmpty = messages.length === 0 && !isTyping;

  return (
    <div
      role="complementary"
      aria-label="AI chat assistant"
      style={{
        width: 400, minWidth: 400, flexShrink: 0,
        background: T.bg, borderLeft: `1px solid ${T.border}`,
        boxShadow: '-4px 0 16px rgba(74,64,50,0.06)',
        display: 'flex', flexDirection: 'column', fontFamily: T.font,
      }}
    >
      {/* Header */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 10,
        padding: '12px 16px', borderBottom: `1px solid ${T.border}`, flexShrink: 0,
      }}>
        <MoziAvatar size={30} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <span style={{ fontSize: 14, fontWeight: 600, color: T.text }}>墨子</span>
          <span style={{ fontSize: 11, color: T.textTertiary, marginLeft: 6 }}>· AI 助手</span>
        </div>
        {aiPhase !== 'idle' && (
          <BtnIcon onClick={onCancel} title="停止">
            <svg width="10" height="10" viewBox="0 0 10 10" fill={T.textSecondary}><rect width="10" height="10" rx="2" /></svg>
          </BtnIcon>
        )}
        <BtnIcon onClick={() => setShowNotifications(!showNotifications)} title="通知">
          <span style={{ fontSize: 14 }}>🔔</span>
        </BtnIcon>
        <BtnIcon onClick={onClose} title="关闭">
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
            <path d="M2 2L10 10M10 2L2 10" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
        </BtnIcon>
      </div>

      {/* Notification Panel */}
      {showNotifications && (
        <div style={{ borderBottom: `1px solid ${T.border}`, flexShrink: 0 }}>
          <NotificationPanel
            onClose={() => setShowNotifications(false)}
            onAction={() => setShowNotifications(false)}
          />
        </div>
      )}

      {/* Messages */}
      <div
        role="log"
        aria-live="polite"
        aria-relevant="additions text"
        style={{
          flex: 1, overflowY: 'auto', padding: '16px 12px',
          display: 'flex', flexDirection: 'column', gap: 12, minHeight: 0,
        }}
        className="quiet-scroll"
      >
        {isEmpty && <EmptyState onQuickAction={onSendMessage} />}

        {messages.slice(-50).map((msg) => (
          <div key={msg.id}>
            {msg.role === 'user' ? (
              /* User bubble — right aligned */
              <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                <div style={{
                  maxWidth: '85%', padding: '10px 14px',
                  borderRadius: '14px 14px 4px 14px',
                  background: T.surface, border: `1px solid ${T.border}`,
                  fontSize: 14, lineHeight: 1.6, color: T.text, wordBreak: 'break-word',
                }}>
                  {msg.content}
                </div>
              </div>
            ) : (
              /* AI bubble — left aligned with avatar */
              <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
                <MoziAvatar size={28} />
                <div style={{ flex: 1, minWidth: 0 }}>
                  {msg.degraded && (
                    <div
                      title={msg.degradationReason || undefined}
                      style={{
                        display: 'inline-flex',
                        alignItems: 'center',
                        marginBottom: 4,
                        padding: '2px 6px',
                        borderRadius: 6,
                        border: `1px solid ${T.border}`,
                        background: T.surface,
                        color: T.textSecondary,
                        fontSize: 11,
                        lineHeight: 1.4,
                      }}
                    >
                      简化回复
                    </div>
                  )}
                  <div style={{
                    fontSize: 14, lineHeight: 1.7, color: T.text, wordBreak: 'break-word',
                  }} className="spirit-bubble-md">
                    <ReactMarkdown
                      remarkPlugins={[remarkGfm]}
                      urlTransform={markdownUrlTransform}
                      components={makeComponents(msg.sources) as Record<string, unknown>}
                    >
                      {preprocessContent(msg.content)}
                    </ReactMarkdown>
                  </div>
                  {/* Source references */}
                  {msg.sources && Object.entries(msg.sources).length > 0 && (
                    <div style={{ marginTop: 4, display: 'flex', flexDirection: 'column', gap: 2 }}>
                      {Object.entries(msg.sources).map(([idx, src]) => (
                        <span
                          key={idx}
                          style={{ fontSize: 11, color: T.accent, cursor: 'pointer' }}
                          onClick={() => onNoteClick?.(src.id)}
                        >
                          [{idx}] {src.title}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}
            {/* Embedded workflow card */}
            {msg.workflowCardId && cards[msg.workflowCardId] && (
              <div style={{ marginLeft: 36, marginTop: 8 }}>
                <AiWorkflowCard
                  cardId={msg.workflowCardId}
                  onExecute={executeWorkflowCard}
                  onRetryExec={retryWorkflowCard}
                />
              </div>
            )}
            {/* Interactive AI card */}
            {msg.cardId && aiCards[msg.cardId] && (
              <div style={{ marginLeft: 36, marginTop: 8 }}>
                <AiCardRenderer
                  card={aiCards[msg.cardId]}
                  onAction={(action, data) => onCardAction?.(msg.cardId!, action, data)}
                />
              </div>
            )}
            {/* Plan approval card */}
            {msg.planData && (
              <div style={{ marginLeft: 36, marginTop: 8 }}>
                <PlanApprovalCard
                  plan={msg.planData}
                  onApprove={(id) => onPlanAction?.('approve', id)}
                  onCancel={(id) => onPlanAction?.('cancel', id)}
                  onPause={(id) => onPlanAction?.('pause', id)}
                  onResume={(id) => onPlanAction?.('resume', id)}
                  onRollback={(id) => onPlanAction?.('rollback', id)}
                  onSkipStep={(planId, stepId) => onPlanAction?.('skipStep', planId, stepId)}
                />
              </div>
            )}
            {/* PENDING_ACTION confirmation card */}
            {msg.pendingAction && (
              <div style={{ marginLeft: 36, marginTop: 8 }}>
                <PendingActionCard
                  actionType={msg.pendingAction.actionType}
                  actionDescription={msg.pendingAction.actionDescription}
                  actionJson={msg.pendingAction.actionJson}
                  details={msg.pendingAction.details}
                  irreversible={msg.pendingAction.irreversible}
                />
              </div>
            )}
          </div>
        ))}

        {/* Typing indicator */}
        {isTyping && (
          <div role="status" aria-live="polite" style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
            <MoziAvatar size={28} />
            <div style={{ flex: 1, minWidth: 0 }}>
              {currentMessage ? (
                <div style={{ fontSize: 14, lineHeight: 1.7, color: T.text }} className="spirit-bubble-md">
                  <ReactMarkdown remarkPlugins={[remarkGfm]} urlTransform={markdownUrlTransform}>{currentMessage}</ReactMarkdown>
                  <span style={{
                    display: 'inline-block', width: 2, height: 14,
                    background: T.accent, marginLeft: 2, verticalAlign: 'middle',
                    animation: 'cursor-blink 1s step-end infinite',
                  }} />
                </div>
              ) : aiSteps.length > 0 ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {aiSteps.map((s, i) => {
                    const isLast = i === aiSteps.length - 1;
                    const isDone = s.step === 'tool_result' || !isLast;
                    return (
                      <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
                        {isDone
                          ? <span style={{ color: T.success }}>&#10003;</span>
                          : <span style={{ color: T.accent, animation: 'spin 1s linear infinite', display: 'inline-block' }}>&#10227;</span>
                        }
                        <span style={{ color: isDone ? T.text : T.textSecondary }}>{s.detail}</span>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '4px 0' }}>
                  <span style={{
                    width: 8, height: 8, borderRadius: '50%', background: T.accent,
                    animation: 'wp-pulse 1.5s ease-in-out infinite',
                  }} />
                  <span style={{ fontSize: 14, color: T.textSecondary }}>墨子正在思考...</span>
                </div>
              )}
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* Quick commands (when has messages) */}
      {!isEmpty && (
        <div style={{
          display: 'flex', gap: 6, padding: '6px 12px',
          borderTop: `1px solid ${T.border}`, flexShrink: 0, overflowX: 'auto',
        }}>
          {['／总结本篇', '／生成导图', '／改写', '／查找'].map((cmd) => (
            <button key={cmd} onClick={() => onSendMessage(cmd.replace('／', '/'))}
              style={{
                flexShrink: 0, padding: '4px 10px', fontSize: 11,
                background: T.surface, border: `1px solid ${T.border}`,
                borderRadius: 20, color: T.textSecondary, cursor: 'pointer',
                fontFamily: 'inherit', transition: 'all 0.15s',
              }}
              onMouseEnter={e => e.currentTarget.style.background = T.surfaceHover}
              onMouseLeave={e => { e.currentTarget.style.background = T.surface; }}
            >
              {cmd}
            </button>
          ))}
        </div>
      )}

      {/* Input */}
      <ChatInput
        onSend={onSendMessage}
        onCancel={onCancel}
        isProcessing={aiPhase !== 'idle'}
        disabled={aiPhase === 'executing'}
      />
    </div>
  );
}

function BtnIcon({ children, onClick, title }: { children: React.ReactNode; onClick: () => void; title: string }) {
  return (
    <button onClick={onClick} title={title} style={{
      width: 28, height: 28, border: 'none', borderRadius: 6,
      background: 'transparent', cursor: 'pointer', display: 'flex',
      alignItems: 'center', justifyContent: 'center',
      color: T.textSecondary, transition: 'all 0.15s',
    }}
      onMouseEnter={e => e.currentTarget.style.background = T.surfaceHover}
      onMouseLeave={e => e.currentTarget.style.background = 'transparent'}
    >
      {children}
    </button>
  );
}

function EmptyState({ onQuickAction }: { onQuickAction: (msg: string) => void }) {
  const actions = ['整理我的笔记', '查找最近修改的内容', '批量添加标签'];
  return (
    <div style={{
      display: 'flex', flexDirection: 'column', alignItems: 'center',
      justifyContent: 'center', padding: '60px 20px', gap: 16,
    }}>
      <MoziAvatar size={48} />
      <div style={{ textAlign: 'center' }}>
        <p style={{ fontSize: 16, fontWeight: 600, color: T.text, margin: 0 }}>你好，我是墨子</p>
        <p style={{ fontSize: 13, color: T.textSecondary, marginTop: 6 }}>你的 AI 笔记助手，随时为你服务</p>
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, justifyContent: 'center' }}>
        {actions.map(a => (
          <button key={a} onClick={() => onQuickAction(a)} style={{
            padding: '8px 14px', fontSize: 13, borderRadius: 20,
            background: T.surface, border: `1px solid ${T.border}`,
            color: T.textSecondary, cursor: 'pointer', fontFamily: 'inherit',
            transition: 'all 0.15s',
          }}
            onMouseEnter={e => e.currentTarget.style.background = T.surfaceHover}
            onMouseLeave={e => { e.currentTarget.style.background = T.surface; }}
          >
            {a}
          </button>
        ))}
      </div>
    </div>
  );
}
