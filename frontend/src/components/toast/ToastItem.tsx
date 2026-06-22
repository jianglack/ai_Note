import type { Toast } from '../../stores/toastStore';
import { useToastStore } from '../../stores/toastStore';

const barColors: Record<Toast['type'], string> = {
  success: 'var(--color-status-success, #6b7a5a)',
  partial: 'var(--color-status-warning, #c9a959)',
  failed: 'var(--color-status-failed, #b8452e)',
  info: 'var(--color-ink-700, #4a4032)',
};

function ToastIcon({ type }: { type: Toast['type'] }) {
  const color = barColors[type];
  switch (type) {
    case 'success':
      return (
        <svg width={16} height={16} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}>
          <path d="M5 12l5 5L19 7" />
        </svg>
      );
    case 'partial':
      return (
        <svg width={16} height={16} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="1.8" strokeLinecap="round" style={{ flexShrink: 0 }}>
          <path d="M12 2L22 20H2L12 2Z" />
          <path d="M12 9v4M12 16v.5" />
        </svg>
      );
    case 'failed':
      return (
        <svg width={16} height={16} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" style={{ flexShrink: 0 }}>
          <path d="M6 6L18 18M18 6L6 18" />
        </svg>
      );
    case 'info':
      return (
        <svg width={16} height={16} viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}>
          <path d="M11.25 11.25l.041-.02a.75.75 0 011.063.852l-.708 2.836a.75.75 0 001.063.853l.041-.021M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-9-3.75h.008v.008H12V8.25z" />
        </svg>
      );
  }
}

export default function ToastItem({ toast }: { toast: Toast }) {
  const { markExiting, removeToast } = useToastStore();

  const handleDismiss = () => {
    markExiting(toast.id);
    setTimeout(() => removeToast(toast.id), 300);
  };

  return (
    <div style={{
      display: 'flex', overflow: 'hidden',
      background: 'var(--color-workflow-card-bg, #fff)',
      border: '1px solid var(--color-border, rgba(74,64,50,0.12))',
      borderRadius: 10,
      boxShadow: '0 8px 24px rgba(74,64,50,0.12)',
      minWidth: 260, maxWidth: 360,
      animation: toast.exiting ? 'wf-slide-out 0.3s ease forwards' : 'wf-slide-in 0.3s ease forwards',
    }}>
      {/* Left color bar */}
      <div style={{
        width: 3, flexShrink: 0, borderRadius: '3px 0 0 3px',
        background: barColors[toast.type],
      }} />

      {/* Content */}
      <div style={{ flex: 1, padding: '12px 14px', display: 'flex', flexDirection: 'column', gap: 4 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <ToastIcon type={toast.type} />
          <span style={{
            flex: 1, fontSize: 14, color: 'var(--color-text, #2b2620)',
            lineHeight: 1.4,
          }}>
            {toast.title}
          </span>
          <svg
            onClick={handleDismiss}
            width={14} height={14} viewBox="0 0 24 24" fill="none"
            stroke="var(--color-text-tertiary, #a09888)" strokeWidth="2" strokeLinecap="round"
            style={{ cursor: 'pointer', flexShrink: 0 }}
          >
            <path d="M6 18L18 6M6 6l12 12" />
          </svg>
        </div>
        {toast.message && (
          <div style={{
            fontSize: 12, color: 'var(--color-text-secondary, #7a6b58)',
            paddingLeft: 24,
          }}>
            {toast.message}
          </div>
        )}
        {toast.action && (
          <div
            onClick={toast.action.onClick}
            style={{
              fontSize: 13, color: 'var(--color-accent, #b8452e)',
              paddingLeft: 24, cursor: 'pointer',
            }}
          >
            {toast.action.label}
          </div>
        )}
      </div>
    </div>
  );
}
