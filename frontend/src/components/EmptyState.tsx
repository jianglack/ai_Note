/**
 * 空状态页 — 严格基于 ai_/handoff/components/Extra.jsx EmptyState
 */
import type { CSSProperties, ReactNode } from 'react';

interface EmptyStateProps {
  onCreateNote: () => void;
  onAiStart: () => void;
  onImport: () => void;
}

const actionCardStyle: CSSProperties = {
  padding: '24px 20px',
  background: 'var(--color-paper-0)',
  border: '1px solid var(--color-border)',
  borderRadius: 'var(--radius-lg)',
  cursor: 'pointer',
  textAlign: 'center',
  boxShadow: 'var(--shadow-sm)',
  appearance: 'none',
  color: 'inherit',
  fontFamily: 'inherit',
  width: '100%',
};

function ActionCard({
  ariaLabel,
  onClick,
  children,
  style,
}: {
  ariaLabel: string;
  onClick: () => void;
  children: ReactNode;
  style?: CSSProperties;
}) {
  return (
    <button
      type="button"
      aria-label={ariaLabel}
      onClick={onClick}
      style={{ ...actionCardStyle, ...style }}
    >
      {children}
    </button>
  );
}

export default function EmptyState({ onCreateNote, onAiStart, onImport }: EmptyStateProps) {
  return (
    <div className="paper-texture" style={{
      width: '100%', height: '100%',
      display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center',
      padding: 48, position: 'relative', fontFamily: 'var(--font-sans)'
    }}>
      {/* 大标题 */}
      <div style={{
        fontFamily: 'var(--font-hand)', fontSize: 48, color: 'var(--color-ink-900)',
        marginBottom: 8, lineHeight: 1.2, textAlign: 'center'
      }}>
        把想法整理成<span className="wavy-underline" style={{ color: 'var(--color-accent)' }}>第一篇笔记</span>
      </div>
      <div style={{ fontSize: 14, color: 'var(--color-ink-500)', marginBottom: 40 }}>
        新建、起草或导入已有内容，直接进入写作。
      </div>

      {/* 三种入口卡 */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 18, maxWidth: 820, width: '100%' }}>
        {/* 空白页 */}
        <ActionCard ariaLabel="Create blank note" onClick={onCreateNote}>
          <span style={{ display: 'block', marginBottom: 10 }}>
            <svg viewBox="0 0 80 80" width="60" height="60">
              <rect x="20" y="14" width="44" height="56" fill="var(--color-paper-0)" stroke="var(--color-ink-700)" strokeWidth="1.5"/>
              <line x1="26" y1="26" x2="56" y2="26" stroke="var(--color-ink-700)" strokeWidth="0.8" opacity="0.5"/>
              <line x1="26" y1="34" x2="48" y2="34" stroke="var(--color-ink-700)" strokeWidth="0.8" opacity="0.5"/>
              <line x1="26" y1="42" x2="52" y2="42" stroke="var(--color-ink-700)" strokeWidth="0.8" opacity="0.5"/>
            </svg>
          </span>
          <span style={{ display: 'block', fontSize: 15, fontWeight: 600, marginBottom: 4, color: 'var(--color-ink-900)' }}>空白页</span>
          <span style={{ display: 'block', fontSize: 12, color: 'var(--color-ink-500)', lineHeight: 1.6, marginBottom: 12 }}>从一张干净的纸开始</span>
          <span style={{
            fontSize: 10, padding: '2px 8px', background: 'var(--color-paper-2)',
            borderRadius: 4, fontFamily: 'monospace', color: 'var(--color-ink-500)'
          }}>Ctrl+N</span>
        </ActionCard>

        {/* 让AI起头 */}
        <ActionCard ariaLabel="Start with AI" onClick={onAiStart} style={{ position: 'relative' }}>
          <span style={{ display: 'block', marginBottom: 10 }}>
            <svg viewBox="0 0 80 80" width="60" height="60">
              <circle cx="40" cy="40" r="22" fill="none" stroke="var(--color-accent)" strokeWidth="1.5"/>
              <path d="M 40 22 L 44 36 L 58 40 L 44 44 L 40 58 L 36 44 L 22 40 L 36 36 Z" fill="var(--color-highlight)" stroke="var(--color-accent)" strokeWidth="1"/>
            </svg>
          </span>
          <span style={{ display: 'block', fontSize: 15, fontWeight: 600, marginBottom: 4, color: 'var(--color-ink-900)' }}>让 AI 起头</span>
          <span style={{ display: 'block', fontSize: 12, color: 'var(--color-ink-500)', lineHeight: 1.6, marginBottom: 12 }}>告诉它你想写什么</span>
          <span style={{
            fontSize: 10, padding: '2px 8px', background: 'var(--color-paper-2)',
            borderRadius: 4, fontFamily: 'monospace', color: 'var(--color-ink-500)'
          }}>Ctrl+K</span>
        </ActionCard>

        {/* 导入 */}
        <ActionCard ariaLabel="Import notes" onClick={onImport}>
          <span style={{ display: 'block', marginBottom: 10 }}>
            <svg viewBox="0 0 80 80" width="60" height="60">
              <path d="M 40 16 L 40 50 M 30 40 L 40 50 L 50 40" fill="none" stroke="var(--color-ink-700)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <path d="M 18 60 L 18 64 L 62 64 L 62 60" fill="none" stroke="var(--color-ink-700)" strokeWidth="2" strokeLinecap="round"/>
            </svg>
          </span>
          <span style={{ display: 'block', fontSize: 15, fontWeight: 600, marginBottom: 4, color: 'var(--color-ink-900)' }}>导入</span>
          <span style={{ display: 'block', fontSize: 12, color: 'var(--color-ink-500)', lineHeight: 1.6, marginBottom: 12 }}>Markdown / 其他笔记应用</span>
        </ActionCard>
      </div>

      {/* 提示 */}
      <div style={{
        marginTop: 40, padding: '12px 20px',
        background: 'var(--color-paper-2)', borderRadius: 999,
        fontSize: 12, color: 'var(--color-ink-500)',
        display: 'flex', alignItems: 'center', gap: 10
      }}>
        <span style={{ color: 'var(--color-accent)' }}>*</span>
        小提示：在任意位置按 <kbd style={{
          padding: '1px 6px', background: 'var(--color-paper-0)', borderRadius: 3,
          border: '1px solid var(--color-border)', fontFamily: 'monospace', fontSize: 11
        }}>/</kbd> 召唤 AI，按 <kbd style={{
          padding: '1px 6px', background: 'var(--color-paper-0)', borderRadius: 3,
          border: '1px solid var(--color-border)', fontFamily: 'monospace', fontSize: 11
        }}>Ctrl+P</kbd> 快速切换笔记
      </div>

      {/* 装饰 · 散落便签 */}
      <div style={{
        position: 'absolute', top: 60, right: 60, width: 100, height: 100,
        background: '#fef4a8', transform: 'rotate(8deg)', padding: 14,
        fontFamily: 'var(--font-hand)', fontSize: 13, color: '#5a4a2a',
        boxShadow: '0 6px 16px rgba(74,64,50,0.15)'
      }}>
        一个想<br/>很久的<br/>句子...
      </div>
      <div style={{
        position: 'absolute', bottom: 80, left: 60, width: 90, height: 90,
        background: '#c8d5b0', transform: 'rotate(-6deg)', padding: 12,
        fontFamily: 'var(--font-hand)', fontSize: 12, color: '#3d4a2a',
        boxShadow: '0 6px 16px rgba(74,64,50,0.15)'
      }}>
        <span style={{ textDecoration: 'line-through' }}>下午的</span><br/>计划<br/>· 茶<br/>· 散步
      </div>
    </div>
  );
}
