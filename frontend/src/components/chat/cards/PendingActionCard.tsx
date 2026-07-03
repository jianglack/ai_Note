import { useState, useCallback } from 'react';
import { saveChatMessages, sendActionFeedback } from '../../../api';
import { useAiStore } from '../../../stores/aiStore';
import { useNoteStore } from '../../../stores/noteStore';
import './ai-cards.css';

type CardState = 'idle' | 'notifying' | 'ai-responding' | 'done';

interface Props {
  actionType: string;
  actionDescription: string;
  actionJson: string;
  details?: { label: string; warn?: boolean }[];
  irreversible?: boolean;
}

export default function PendingActionCard({
  actionType, actionDescription, actionJson, details, irreversible,
}: Props) {
  const [state, setState] = useState<CardState>('idle');
  const [choice, setChoice] = useState<'confirm' | 'cancel' | null>(null);
  const [followUpMessage, setFollowUpMessage] = useState<string | null>(null);
  const ai = useAiStore();

  const handleChoose = useCallback(async (decision: 'confirm' | 'cancel') => {
    setChoice(decision);
    setState('notifying');

    try {
      const response = await sendActionFeedback({
        actionJson,
        confirmed: decision === 'confirm',
      });

      if (decision === 'confirm') {
        try {
          await useNoteStore.getState().loadData();
        } catch (refreshErr) {
          console.warn('Failed to refresh notes after action feedback:', refreshErr);
        }
      }

      setState('ai-responding');

      // Add the AI follow-up message to chat
      if (response?.content) {
        setFollowUpMessage(response.content);
        try {
          await saveChatMessages(decision === 'confirm' ? '确认执行' : '取消执行', response.content);
        } catch (saveErr) {
          console.warn('Failed to persist action feedback chat:', saveErr);
        }
        setTimeout(() => {
          ai.addMessage({
            id: `feedback-${Date.now()}`,
            content: response.content,
            timestamp: Date.now(),
            role: 'spirit',
          });
          setState('done');
        }, 600);
      } else {
        setState('done');
      }
    } catch (err) {
      console.error('Action feedback failed:', err);
      setState('done');
      setFollowUpMessage('操作反馈发送失败，请稍后重试。');
    }
  }, [actionJson, ai]);

  const resolved = state !== 'idle';
  const confirmed = choice === 'confirm';
  const cancelled = choice === 'cancel';

  return (
    <div className="pending-action-card">
      {/* Type strip */}
      <div className="pac-type-strip">
        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
          <path d="M12 9v4M12 17h.01" /><path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z" />
        </svg>
        <span className="pac-type-label">PENDING_ACTION · 需要你确认</span>
      </div>

      {/* Body */}
      <div className="pac-body">
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div className="pac-icon-card">
            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" style={{ color: 'var(--color-accent)' }}>
              <path d="M3 6h18M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
            </svg>
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <div style={{ fontSize: 12.5, color: 'var(--color-text-secondary)', marginBottom: 2 }}>即将执行：</div>
            <div style={{ fontFamily: 'var(--font-serif)', fontSize: 16, fontWeight: 600, color: 'var(--color-text)', lineHeight: 1.35 }}>
              {actionDescription}
            </div>
          </div>
        </div>
        {details && details.length > 0 && (
          <div style={{ display: 'flex', gap: 6, marginTop: 10, flexWrap: 'wrap' }}>
            {details.map((d, i) => (
              <span key={i} className={`pac-chip${d.warn ? ' warn' : ''}`}>
                {d.label}
              </span>
            ))}
            {irreversible && (
              <span className="pac-chip warn">⚠ 不可撤销</span>
            )}
          </div>
        )}
      </div>

      {/* Buttons */}
      <div className="pac-buttons">
        <button
          disabled={resolved}
          onClick={() => !resolved && handleChoose('confirm')}
          className={`pac-btn${confirmed ? ' confirmed' : ' primary'}`}
        >
          {confirmed && (
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
          )}
          {confirmed ? '已确认' : '确认执行'}
        </button>
        <button
          disabled={resolved}
          onClick={() => !resolved && handleChoose('cancel')}
          className={`pac-btn${cancelled ? ' cancelled' : ' ghost'}`}
        >
          {cancelled && (
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><path d="M18 6 6 18M6 6l12 12"/></svg>
          )}
          {cancelled ? '已取消' : '取消'}
        </button>
        <span style={{ flex: 1 }} />
        {!resolved && <span className="pac-hint">不会自动执行</span>}
        {resolved && (
          <span className="pac-timestamp">
            {confirmed ? '✓' : '✗'} {new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
          </span>
        )}
      </div>

      {/* State footer */}
      {state === 'notifying' && (
        <div className="pac-footer neutral">
          <span className="pac-ink-spread" />
          <span>正在通知墨子你的决定…</span>
          <span style={{ flex: 1 }} />
          <span className="pac-api-hint">POST /action-feedback</span>
        </div>
      )}
      {(state === 'ai-responding' || state === 'done') && (
        <div className={`pac-footer ${confirmed ? 'success' : 'cancel'}`}>
          {confirmed ? (
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
          ) : (
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M3 7v6h6"/><path d="M3 13a9 9 0 1 0 3-7"/></svg>
          )}
          <span style={{ fontWeight: 500 }}>
            墨子已收到 · {confirmed ? '正在执行' : '将换种方式继续'}
          </span>
        </div>
      )}
    </div>
  );
}
