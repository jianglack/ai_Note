import type { WorkflowCard, WorkflowState } from '../../stores/workflowStore';
import { useWorkflowStore } from '../../stores/workflowStore';
import WorkflowCheckbox from './WorkflowCheckbox';
import WorkflowProgressBar from './WorkflowProgressBar';
import WorkflowNoteRow from './WorkflowNoteRow';
import WorkflowActionBar from './WorkflowActionBar';
import WorkflowResultBanner from './WorkflowResultBanner';

interface Props {
  cardId: string;
  onExecute?: (cardId: string) => void;
  onRetryExec?: (cardId: string) => void;
}

function getHeaderTitle(card: WorkflowCard): string {
  const count = card.items.length;
  const label = card.operationLabel;
  switch (card.state) {
    case 'thinking':
      return `${label}`;
    case 'awaiting_confirmation':
      return `待确认：${label} ${count} 篇笔记`;
    case 'executing':
      return `正在执行：${label} ${count} 篇笔记`;
    case 'done':
      return `已完成：成功${label} ${card.result?.successCount ?? count} 篇笔记`;
    case 'partial_failed':
      return `部分完成：成功 ${card.result?.successCount ?? 0} 篇，失败 ${card.result?.failedCount ?? 0} 篇`;
    case 'failed':
      return `执行失败：${label}未完成`;
    case 'cancelled':
      return `已取消：${label}`;
    default:
      return label;
  }
}

// State-specific header icon color
function getHeaderColor(state: WorkflowState): string {
  switch (state) {
    case 'done':
      return 'var(--color-status-success, #6b7a5a)';
    case 'partial_failed':
      return 'var(--color-status-warning, #c9a959)';
    case 'failed':
      return 'var(--color-status-failed, #b8452e)';
    case 'cancelled':
      return 'var(--color-text-tertiary, #a09888)';
    default:
      return 'var(--color-text, #2b2620)';
  }
}

export default function AiWorkflowCard({ cardId, onExecute, onRetryExec }: Props) {
  const card = useWorkflowStore((s) => s.cards[cardId]);
  const { toggleItem, toggleAll, cancel } = useWorkflowStore();

  if (!card) return null;

  const selectedCount = card.items.filter((i) => i.selected).length;
  const failedItems = card.items.filter((i) => i.status === 'failed');
  const allSelected = card.items.length > 0 && card.items.every((i) => i.selected);
  const someSelected = card.items.some((i) => i.selected) && !allSelected;

  return (
    <div
      style={{
        borderRadius: 12,
        border: '1px solid var(--color-border, rgba(74,64,50,0.12))',
        background: 'var(--color-workflow-card-bg, #fff)',
        boxShadow: 'var(--color-workflow-shadow, 0 4px 12px rgba(74,64,50,0.08))',
        overflow: 'hidden',
        opacity: card.state === 'cancelled' ? 0.5 : 1,
        pointerEvents: card.state === 'cancelled' ? 'none' : undefined,
      }}
      className="wf-fade-in"
    >
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8,
        padding: 12,
        borderBottom: '1px solid var(--color-border, rgba(74,64,50,0.12))',
      }}>
        <HeaderIcon state={card.state} type={card.operationType} />
        <span style={{
          fontSize: 15, fontWeight: 600,
          color: getHeaderColor(card.state),
          flex: 1, minWidth: 0,
        }}>
          {getHeaderTitle(card)}
        </span>
      </div>

      <div style={{ padding: '10px 12px', display: 'flex', flexDirection: 'column', gap: 10 }}>
        {card.state === 'thinking' && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 14 }}>
              <span style={{
                width: 8, height: 8, borderRadius: '50%',
                background: 'var(--color-accent, #b8452e)',
              }} className="wf-pulse" />
              <span style={{ color: 'var(--color-text-secondary, #7a6b58)' }}>墨子正在思考...</span>
            </div>
            {card.thinkingSteps.length > 0 && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, paddingLeft: 16 }}>
                {card.thinkingSteps.map((step, i) => (
                  <div key={i} style={{ display: 'flex', alignItems: 'flex-start', gap: 8, fontSize: 12 }}>
                    {step.done ? (
                      <svg style={{ width: 12, height: 12, color: 'var(--color-status-success, #6b7a5a)', marginTop: 2, flexShrink: 0 }} viewBox="0 0 12 12" fill="none">
                        <path d="M2 6L5 9L10 3" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                      </svg>
                    ) : (
                      <svg style={{ width: 12, height: 12, color: 'var(--color-accent, #b8452e)', marginTop: 2, flexShrink: 0 }} className="wf-spin" viewBox="0 0 12 12" fill="none">
                        <circle cx="6" cy="6" r="4.5" stroke="currentColor" strokeWidth="1.2" strokeDasharray="16 8" />
                      </svg>
                    )}
                    <div>
                      <span style={{ color: 'var(--color-text, #2b2620)' }}>{step.step}</span>
                      {step.detail && (
                        <span style={{ color: 'var(--color-text-tertiary, #a09888)', marginLeft: 6 }}>{step.detail}</span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {card.state !== 'thinking' && card.state !== 'cancelled' && (
          <>
            {card.state === 'awaiting_confirmation' && card.items.length > 1 && (
              <div style={{
                display: 'flex', alignItems: 'center', gap: 8,
                padding: '6px 0', borderBottom: '1px solid var(--color-border, rgba(74,64,50,0.12))',
              }}>
                <WorkflowCheckbox
                  checked={allSelected}
                  indeterminate={someSelected}
                  onChange={() => toggleAll(cardId)}
                />
                <span style={{ fontSize: 12, color: 'var(--color-text-secondary, #7a6b58)' }}>
                  已选 {selectedCount}/{card.items.length}
                </span>
              </div>
            )}

            {(card.state === 'executing' || card.state === 'done' || card.state === 'partial_failed' || card.state === 'failed') && (
              <WorkflowProgressBar
                current={card.progress.current}
                total={card.progress.total}
                status={card.state === 'executing' ? 'executing' : card.state === 'done' ? 'done' : card.state === 'partial_failed' ? 'partial_failed' : 'failed'}
              />
            )}

            {card.result && (card.state === 'done' || card.state === 'partial_failed' || card.state === 'failed') && (
              <WorkflowResultBanner
                state={card.state}
                operationLabel={card.operationLabel}
                result={card.result}
                failedItems={failedItems}
              />
            )}

            <div style={{ maxHeight: 280, overflowY: 'auto' }} className="quiet-scroll">
              {card.items.map((item) => (
                <WorkflowNoteRow
                  key={item.noteId}
                  item={item}
                  showCheckbox={card.state === 'awaiting_confirmation'}
                  onToggle={() => toggleItem(cardId, item.noteId)}
                />
              ))}
            </div>

            <WorkflowActionBar
              state={card.state}
              selectedCount={selectedCount}
              failedCount={failedItems.length}
              operationLabel={card.operationLabel}
              operationType={card.operationType}
              onConfirm={() => onExecute?.(cardId)}
              onCancel={() => cancel(cardId)}
              onRetry={() => onRetryExec?.(cardId)}
            />
          </>
        )}
      </div>
    </div>
  );
}

function HeaderIcon({ state, type }: { state: WorkflowState; type: string }) {
  const size = 18;

  // State-specific icons
  if (state === 'thinking') {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="var(--color-accent, #b8452e)" strokeWidth="1.8" strokeLinecap="round" className="wf-spin">
        <circle cx="12" cy="12" r="9" strokeDasharray="32 16" />
      </svg>
    );
  }
  if (state === 'executing') {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="var(--color-accent, #b8452e)" strokeWidth="1.8" strokeLinecap="round" className="wf-spin" style={{ animationDuration: '1.5s' }}>
        <path d="M12 2v4m0 12v4m10-10h-4M6 12H2m15.07-7.07l-2.83 2.83M9.76 14.24l-2.83 2.83m11.14 0l-2.83-2.83M9.76 9.76L6.93 6.93" />
      </svg>
    );
  }
  if (state === 'done') {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="var(--color-status-success, #6b7a5a)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M5 12l5 5L19 7" />
      </svg>
    );
  }
  if (state === 'partial_failed') {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="var(--color-status-warning, #c9a959)" strokeWidth="1.8" strokeLinecap="round">
        <path d="M12 2L22 20H2L12 2Z" />
        <path d="M12 9v4M12 16v.5" />
      </svg>
    );
  }
  if (state === 'failed') {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="var(--color-status-failed, #b8452e)" strokeWidth="2" strokeLinecap="round">
        <path d="M6 6L18 18M18 6L6 18" />
      </svg>
    );
  }
  if (state === 'cancelled') {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="var(--color-text-tertiary, #a09888)" strokeWidth="2" strokeLinecap="round">
        <path d="M6 6L18 18M18 6L6 18" />
      </svg>
    );
  }

  // Operation-type icons for awaiting_confirmation
  switch (type) {
    case 'delete':
    case 'permanent_delete':
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={type === 'permanent_delete' ? '#8b2f1e' : 'var(--color-accent, #b8452e)'} strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <path d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" />
        </svg>
      );
    case 'restore':
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="var(--color-status-success, #6b7a5a)" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <path d="M9 15L3 9m0 0l6-6M3 9h12a6 6 0 010 12h-3" />
        </svg>
      );
    case 'move':
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="var(--color-ink-700, #4a4032)" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <path d="M2.25 12.75V12A2.25 2.25 0 014.5 9.75h15A2.25 2.25 0 0121.75 12v.75m-8.69-6.44l-2.12-2.12a1.5 1.5 0 00-1.061-.44H4.5A2.25 2.25 0 002.25 6v12a2.25 2.25 0 002.25 2.25h15A2.25 2.25 0 0021.75 18V9a2.25 2.25 0 00-2.25-2.25h-5.379a1.5 1.5 0 01-1.06-.44z" />
        </svg>
      );
    case 'tag':
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="var(--color-ink-700, #4a4032)" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <path d="M9.568 3H5.25A2.25 2.25 0 003 5.25v4.318c0 .597.237 1.17.659 1.591l9.581 9.581c.699.699 1.78.872 2.607.33a18.095 18.095 0 005.223-5.223c.542-.827.369-1.908-.33-2.607L11.16 3.66A2.25 2.25 0 009.568 3zM6 6h.008v.008H6V6z" />
        </svg>
      );
    case 'remove_tag':
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="var(--color-status-warning, #c9a959)" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <path d="M9.568 3H5.25A2.25 2.25 0 003 5.25v4.318c0 .597.237 1.17.659 1.591l9.581 9.581c.699.699 1.78.872 2.607.33a18.095 18.095 0 005.223-5.223c.542-.827.369-1.908-.33-2.607L11.16 3.66A2.25 2.25 0 009.568 3z" />
          <line x1="4" y1="20" x2="20" y2="4" strokeWidth="2" />
        </svg>
      );
    default:
      return (
        <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="var(--color-accent, #b8452e)" strokeWidth="1.8" strokeLinecap="round">
          <path d="M8 2V14M2 8H14" />
        </svg>
      );
  }
}
