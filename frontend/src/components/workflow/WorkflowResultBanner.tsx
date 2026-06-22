import { useState } from 'react';
import type { WorkflowState, WorkflowItem } from '../../stores/workflowStore';

interface Props {
  state: WorkflowState;
  operationLabel: string;
  result: { successCount: number; failedCount: number };
  failedItems: WorkflowItem[];
}

const stateConfig = {
  done: {
    bg: 'var(--color-status-success-light, rgba(107,122,90,0.1))',
    border: 'var(--color-status-success, #6b7a5a)',
    color: 'var(--color-status-success, #6b7a5a)',
  },
  partial_failed: {
    bg: 'var(--color-status-warning-light, rgba(201,169,89,0.1))',
    border: 'var(--color-status-warning, #c9a959)',
    color: 'var(--color-status-warning, #c9a959)',
  },
  failed: {
    bg: 'var(--color-status-failed-light, rgba(184,69,46,0.08))',
    border: 'var(--color-status-failed, #b8452e)',
    color: 'var(--color-status-failed, #b8452e)',
  },
} as const;

export default function WorkflowResultBanner({ state, operationLabel, result, failedItems }: Props) {
  const [expanded, setExpanded] = useState(false);

  const cfg = stateConfig[state as keyof typeof stateConfig];
  if (!cfg) return null;

  if (state === 'done') {
    return (
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8,
        padding: '10px 12px', borderRadius: 10,
        borderLeft: `3px solid ${cfg.border}`,
        background: cfg.bg,
      }}>
        <svg width={16} height={16} viewBox="0 0 24 24" fill="none" stroke={cfg.color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}>
          <path d="M5 12l5 5L19 7" />
        </svg>
        <span style={{ fontSize: 13, color: 'var(--color-text, #2b2620)' }}>
          {result.successCount} 篇笔记已{operationLabel}
        </span>
      </div>
    );
  }

  if (state === 'partial_failed') {
    return (
      <div style={{ borderRadius: 10, overflow: 'hidden' }}>
        <button
          onClick={() => setExpanded(!expanded)}
          style={{
            width: '100%', display: 'flex', alignItems: 'center', gap: 8,
            padding: '10px 12px',
            borderLeft: `3px solid ${cfg.border}`,
            background: cfg.bg,
            border: 'none', borderLeftWidth: 3, borderLeftStyle: 'solid', borderLeftColor: cfg.border,
            cursor: 'pointer', fontFamily: 'inherit',
          }}
        >
          <svg width={16} height={16} viewBox="0 0 24 24" fill="none" stroke={cfg.color} strokeWidth="1.8" strokeLinecap="round" style={{ flexShrink: 0 }}>
            <path d="M12 2L22 20H2L12 2Z" />
            <path d="M12 9v4M12 16v.5" />
          </svg>
          <span style={{ flex: 1, textAlign: 'left', fontSize: 13, color: 'var(--color-text, #2b2620)' }}>
            成功 {result.successCount} 篇，失败 {result.failedCount} 篇
          </span>
          <svg
            width={14} height={14} viewBox="0 0 14 14" fill="none"
            stroke={cfg.color} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"
            style={{ transition: 'transform 0.2s', transform: expanded ? 'rotate(180deg)' : 'none' }}
          >
            <path d="M3 5L7 9L11 5" />
          </svg>
        </button>
        {expanded && failedItems.length > 0 && (
          <div style={{
            padding: '8px 12px', paddingLeft: 24,
            background: 'var(--color-status-failed-light, rgba(184,69,46,0.08))',
            borderTop: '1px solid rgba(184,69,46,0.1)',
            display: 'flex', flexDirection: 'column', gap: 4,
          }}>
            {failedItems.map((item) => (
              <div key={item.noteId} style={{ fontSize: 11, color: 'var(--color-status-failed, #b8452e)' }}>
                • {item.title} — {item.error || '未知错误'}
              </div>
            ))}
          </div>
        )}
      </div>
    );
  }

  if (state === 'failed') {
    return (
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8,
        padding: '10px 12px', borderRadius: 10,
        borderLeft: `3px solid ${cfg.border}`,
        background: cfg.bg,
      }}>
        <svg width={16} height={16} viewBox="0 0 24 24" fill="none" stroke={cfg.color} strokeWidth="2" strokeLinecap="round" style={{ flexShrink: 0 }}>
          <path d="M6 6L18 18M18 6L6 18" />
        </svg>
        <span style={{ fontSize: 13, color: 'var(--color-text, #2b2620)' }}>
          操作失败，{result.failedCount} 篇笔记未{operationLabel}
        </span>
      </div>
    );
  }

  return null;
}
