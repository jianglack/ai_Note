import type { WorkflowItem } from '../../stores/workflowStore';
import WorkflowCheckbox from './WorkflowCheckbox';

interface Props {
  item: WorkflowItem;
  showCheckbox: boolean;
  onToggle?: () => void;
}

function StatusIcon({ status }: { status: WorkflowItem['status'] }) {
  switch (status) {
    case 'waiting':
      return (
        <span style={{
          width: 8, height: 8, borderRadius: '50%',
          background: 'var(--color-text-tertiary, #a09888)',
          flexShrink: 0,
        }} />
      );
    case 'executing':
      return (
        <svg width={14} height={14} viewBox="0 0 24 24" fill="none"
          stroke="var(--color-accent, #b8452e)" strokeWidth="2" strokeLinecap="round"
          className="wf-spin" style={{ flexShrink: 0 }}
        >
          <path d="M12 2v4m0 12v4m10-10h-4M6 12H2m15.07-7.07l-2.83 2.83M9.76 14.24l-2.83 2.83m11.14 0l-2.83-2.83M9.76 9.76L6.93 6.93" />
        </svg>
      );
    case 'success':
      return (
        <svg width={14} height={14} viewBox="0 0 24 24" fill="none"
          stroke="var(--color-status-success, #6b7a5a)" strokeWidth="2"
          strokeLinecap="round" strokeLinejoin="round" style={{ flexShrink: 0 }}
        >
          <path d="M5 12l5 5L19 7" />
        </svg>
      );
    case 'failed':
      return (
        <svg width={14} height={14} viewBox="0 0 24 24" fill="none"
          stroke="var(--color-status-failed, #b8452e)" strokeWidth="2"
          strokeLinecap="round" style={{ flexShrink: 0 }}
        >
          <path d="M6 6L18 18M18 6L6 18" />
        </svg>
      );
  }
}

export default function WorkflowNoteRow({ item, showCheckbox, onToggle }: Props) {
  return (
    <div
      onClick={showCheckbox ? onToggle : undefined}
      style={{
        display: 'flex', alignItems: 'center', gap: 10,
        padding: '8px 12px',
        borderBottom: '1px solid var(--color-border, rgba(74,64,50,0.12))',
        cursor: showCheckbox ? 'pointer' : 'default',
        transition: 'background 0.15s',
        background: item.status === 'failed'
          ? 'var(--color-status-failed-light, rgba(184,69,46,0.08))'
          : undefined,
      }}
      onMouseEnter={e => { if (showCheckbox) e.currentTarget.style.background = 'var(--color-surface-hover, #efe8d6)'; }}
      onMouseLeave={e => {
        e.currentTarget.style.background = item.status === 'failed'
          ? 'var(--color-status-failed-light, rgba(184,69,46,0.08))'
          : '';
      }}
    >
      {showCheckbox && (
        <WorkflowCheckbox
          checked={item.selected}
          onChange={onToggle}
        />
      )}

      <span style={{
        flex: 1, fontSize: 13,
        color: 'var(--color-text, #2b2620)',
        overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
      }}>
        {item.title}
      </span>

      {item.folder && (
        <span style={{
          fontSize: 11, color: 'var(--color-text-tertiary, #a09888)',
          flexShrink: 0,
        }}>
          {item.folder}
        </span>
      )}

      <StatusIcon status={item.status} />

      {item.error && (
        <span style={{
          fontSize: 11, color: 'var(--color-status-failed, #b8452e)',
          overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
          maxWidth: 120,
        }} title={item.error}>
          {item.error}
        </span>
      )}
    </div>
  );
}
