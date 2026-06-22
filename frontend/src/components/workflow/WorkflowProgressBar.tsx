interface Props {
  current: number;
  total: number;
  status: 'executing' | 'done' | 'partial_failed' | 'failed';
}

const barColors: Record<Props['status'], string> = {
  executing: 'var(--color-accent, #b8452e)',
  done: 'var(--color-status-success, #6b7a5a)',
  partial_failed: 'var(--color-status-warning, #c9a959)',
  failed: 'var(--color-status-failed, #b8452e)',
};

export default function WorkflowProgressBar({ current, total, status }: Props) {
  const pct = total > 0 ? Math.round((current / total) * 100) : 0;

  return (
    <div style={{ padding: '4px 0' }}>
      <div style={{
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        marginBottom: 6,
      }}>
        <span style={{ fontSize: 12, color: 'var(--color-text-secondary, #7a6b58)' }}>执行进度</span>
        <span style={{ fontSize: 12, color: 'var(--color-text-secondary, #7a6b58)', fontVariantNumeric: 'tabular-nums' }}>
          {current}/{total} 已完成
        </span>
      </div>
      <div style={{
        width: '100%', height: 4, borderRadius: 2,
        background: 'var(--color-workflow-progress-bg, #ede5d1)',
        overflow: 'hidden',
      }}>
        <div
          style={{
            height: '100%', borderRadius: 2,
            background: barColors[status],
            width: `${pct}%`,
            transition: 'width 0.3s ease-out',
          }}
        />
      </div>
    </div>
  );
}
