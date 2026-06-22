import { useState, useRef, useEffect } from 'react';

const T = {
  bg: 'var(--color-paper-0, #fbf7ee)',
  surface: 'var(--color-paper-1, #f5efe0)',
  border: 'rgba(74,64,50,0.12)',
  text: 'var(--color-text, #2b2620)',
  textSecondary: 'var(--color-text-secondary, #7a6b58)',
  accent: 'var(--color-accent, #b8452e)',
};

interface Props {
  onSend: (message: string) => void;
  onCancel: () => void;
  isProcessing: boolean;
  disabled?: boolean;
}

export default function ChatInput({ onSend, onCancel, isProcessing, disabled }: Props) {
  const [value, setValue] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (!isProcessing && textareaRef.current) {
      textareaRef.current.focus();
    }
  }, [isProcessing]);

  const handleSend = () => {
    if (value.trim()) {
      onSend(value.trim());
      setValue('');
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (isProcessing) onCancel();
      else handleSend();
    }
  };

  return (
    <div style={{
      padding: '10px 12px', borderTop: `1px solid ${T.border}`, flexShrink: 0,
    }}>
      <div style={{
        display: 'flex', alignItems: 'flex-end', gap: 8,
        padding: '8px 12px', borderRadius: 12,
        background: T.surface, border: `1px solid ${T.border}`,
      }}>
        <textarea
          ref={textareaRef}
          value={value}
          onChange={e => setValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={isProcessing ? '墨子正在处理...' : '问墨子任何事...'}
          disabled={disabled}
          rows={2}
          style={{
            flex: 1, border: 'none', background: 'transparent', outline: 'none',
            resize: 'none', fontSize: 14, color: T.text, lineHeight: 1.6,
            fontFamily: 'inherit',
          }}
        />
        {isProcessing ? (
          <button onClick={onCancel} title="停止" style={{
            width: 32, height: 32, borderRadius: 8, border: 'none', flexShrink: 0,
            background: T.accent, color: '#fff', cursor: 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <svg width="10" height="10" viewBox="0 0 10 10" fill="currentColor">
              <rect width="10" height="10" rx="2" />
            </svg>
          </button>
        ) : (
          <button onClick={handleSend} disabled={!value.trim()} title="发送" style={{
            width: 32, height: 32, borderRadius: 8, border: 'none', flexShrink: 0,
            background: T.accent, color: '#fff', cursor: value.trim() ? 'pointer' : 'not-allowed',
            opacity: value.trim() ? 1 : 0.35,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            transition: 'opacity 0.15s',
          }}>
            <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor">
              <path d="M2 8L14 2L9 14L7.5 9Z" />
            </svg>
          </button>
        )}
      </div>
    </div>
  );
}
