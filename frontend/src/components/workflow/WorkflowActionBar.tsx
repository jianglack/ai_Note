import type { WorkflowState, OperationType } from '../../stores/workflowStore';

interface Props {
  state: WorkflowState;
  selectedCount: number;
  failedCount: number;
  operationLabel: string;
  operationType?: OperationType;
  onConfirm: () => void;
  onCancel: () => void;
  onRetry: () => void;
  onCollapse?: () => void;
}

function getConfirmStyle(opType?: OperationType): { bg: string; text: string } {
  switch (opType) {
    case 'delete':
      return { bg: 'var(--color-accent, #b8452e)', text: '#fff' };
    case 'permanent_delete':
      return { bg: '#8b2f1e', text: '#fff' };
    case 'restore':
      return { bg: 'var(--color-status-success, #6b7a5a)', text: '#fff' };
    case 'tag':
    case 'move':
      return { bg: 'var(--color-ink-700, #4a4032)', text: '#fff' };
    case 'remove_tag':
      return { bg: 'var(--color-warning, #c9a959)', text: '#fff' };
    default:
      return { bg: 'var(--color-ink-900, #2b2620)', text: '#fff' };
  }
}

function getRetryStyle(opType?: OperationType): { bg: string; text: string } {
  switch (opType) {
    case 'delete':
    case 'permanent_delete':
      return { bg: 'var(--color-accent, #b8452e)', text: '#fff' };
    case 'restore':
      return { bg: 'var(--color-status-success, #6b7a5a)', text: '#fff' };
    default:
      return { bg: 'var(--color-status-warning, #c9a959)', text: '#fff' };
  }
}

export default function WorkflowActionBar({
  state,
  selectedCount,
  failedCount,
  operationLabel,
  operationType,
  onConfirm,
  onCancel,
  onRetry,
  onCollapse,
}: Props) {
  if (state === 'cancelled') return null;

  const confirmStyle = getConfirmStyle(operationType);
  const retryStyle = getRetryStyle(operationType);

  const btnBase: React.CSSProperties = {
    border: 'none',
    borderRadius: 8,
    padding: '7px 16px',
    fontSize: 13,
    fontWeight: 500,
    cursor: 'pointer',
    transition: 'all 0.15s',
    fontFamily: 'inherit',
    whiteSpace: 'nowrap',
  };

  return (
    <div style={{
      display: 'flex', alignItems: 'center', justifyContent: 'flex-end',
      gap: 8, paddingTop: 10,
      borderTop: '1px solid var(--color-border, rgba(74,64,50,0.12))',
    }}>
      {state === 'awaiting_confirmation' && (
        <>
          <button onClick={onCancel} style={{
            ...btnBase,
            background: 'var(--color-surface-hover, #efe8d6)',
            color: 'var(--color-text-secondary, #7a6b58)',
          }}>
            取消
          </button>
          <button
            onClick={onConfirm}
            disabled={selectedCount === 0}
            style={{
              ...btnBase,
              background: selectedCount === 0 ? 'var(--color-surface-hover, #efe8d6)' : confirmStyle.bg,
              color: selectedCount === 0 ? 'var(--color-text-tertiary, #a09888)' : confirmStyle.text,
              cursor: selectedCount === 0 ? 'not-allowed' : 'pointer',
            }}
          >
            确认{operationLabel} ({selectedCount})
          </button>
        </>
      )}

      {state === 'executing' && (
        <button disabled style={{
          ...btnBase,
          background: 'var(--color-surface-hover, #efe8d6)',
          color: 'var(--color-text-tertiary, #a09888)',
          cursor: 'not-allowed',
          display: 'flex', alignItems: 'center', gap: 6,
        }}>
          <svg className="wf-spin" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M12 2v4m0 12v4m10-10h-4M6 12H2m15.07-7.07l-2.83 2.83M9.76 14.24l-2.83 2.83m11.14 0l-2.83-2.83M9.76 9.76L6.93 6.93" strokeLinecap="round" />
          </svg>
          执行中...
        </button>
      )}

      {state === 'done' && (
        <button onClick={onCollapse || onCancel} style={{
          ...btnBase,
          background: 'transparent',
          color: 'var(--color-text-secondary, #7a6b58)',
        }}>
          收起
        </button>
      )}

      {(state === 'partial_failed' || state === 'failed') && (
        <>
          <button onClick={onCancel} style={{
            ...btnBase,
            background: 'transparent',
            color: 'var(--color-text-secondary, #7a6b58)',
          }}>
            关闭
          </button>
          <button onClick={onRetry} style={{
            ...btnBase,
            background: retryStyle.bg,
            color: retryStyle.text,
          }}>
            重试失败项 ({failedCount})
          </button>
        </>
      )}
    </div>
  );
}
