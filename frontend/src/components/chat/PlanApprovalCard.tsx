import { useState } from 'react';
import type { TaskPlanData, TaskStepData } from '../../api';
import './plan-card.css';

interface PlanApprovalCardProps {
  plan: TaskPlanData;
  onApprove: (planId: string) => void;
  onCancel: (planId: string) => void;
  onPause: (planId: string) => void;
  onResume: (planId: string) => void;
  onRollback: (planId: string) => void;
  onSkipStep: (planId: string, stepId: string) => void;
}

const PLAN_STATUS: Record<string, { text: string; cls: string }> = {
  AWAITING_APPROVAL: { text: '待审批', cls: 'warn' },
  EXECUTING: { text: '执行中', cls: 'active' },
  PAUSED: { text: '已暂停', cls: 'warn' },
  COMPLETED: { text: '已完成', cls: 'success' },
  FAILED: { text: '失败', cls: 'error' },
  CANCELLED: { text: '已取消', cls: 'muted' },
  CANCELLED_PARTIAL: { text: '部分取消', cls: 'muted' },
};

function StepRow({ step, idx }: { step: TaskStepData; idx: number }) {
  const s = step.status;
  const isDone = s === 'SUCCESS';
  const isRunning = s === 'IN_PROGRESS';
  const isFailed = s === 'FAILED';
  const isSkipped = s === 'SKIPPED';
  const isInserted = step.action?.startsWith('AUTO_');

  return (
    <div className={`plc-step${isInserted ? ' inserted' : ''}`}>
      {isInserted && <div className="plc-step-bar" />}

      <div className={`plc-step-circle ${isDone ? 'done' : isRunning ? 'running' : isFailed ? 'failed' : 'pending'}`}>
        {isDone ? (
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
        ) : isRunning ? (
          <span className="plc-ink-dot" />
        ) : isFailed ? (
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round"><path d="M18 6 6 18M6 6l12 12"/></svg>
        ) : (
          idx + 1
        )}
      </div>

      <div className="plc-step-content">
        <div className="plc-step-title-row">
          <span className={`plc-step-title${isDone ? ' done' : ''}`}>
            {step.description}
          </span>
          {isInserted && (
            <span className="plc-inserted-badge">
              <svg width="9" height="9" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2l2.4 7.2H22l-6 4.8 2.4 7.2L12 16.4l-6 4.8 2.4-7.2-6-4.8h7.6z"/></svg>
              自动插入
            </span>
          )}
          {isSkipped && <span className="plc-skip-badge">已跳过</span>}
          {step.action && (
            <span className="plc-tool-tag">· {step.action}</span>
          )}
        </div>
        {step.inputParams && (
          <div className="plc-step-detail">{step.inputParams}</div>
        )}
        {isRunning && (
          <div className="plc-step-substatus">
            <span className="plc-sub-dot" />
            正在执行中…
          </div>
        )}
        {step.errorMessage && (
          <div className="plc-step-error">{step.errorMessage}</div>
        )}
      </div>

      {isDone && (
        <span className="plc-duration">✓</span>
      )}
    </div>
  );
}

export default function PlanApprovalCard({
  plan, onApprove, onCancel, onPause, onResume, onRollback, onSkipStep
}: PlanApprovalCardProps) {
  const [expanded, setExpanded] = useState(true);
  const ps = PLAN_STATUS[plan.status] || { text: plan.status, cls: 'muted' };
  const progress = plan.totalSteps > 0 ? (plan.completedSteps / plan.totalSteps) * 100 : 0;
  const isTerminal = ['COMPLETED', 'CANCELLED', 'CANCELLED_PARTIAL'].includes(plan.status);

  return (
    <div className="plc-card">
      <div className="plc-header">
        <div className="plc-header-icon">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
            <path d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2"/>
            <path d="M9 5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v0a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2z"/>
            <path d="M9 12h6M9 16h6"/>
          </svg>
        </div>
        <div className="plc-header-text">
          <div className="plc-header-title">{plan.goal}</div>
          <div className="plc-header-meta">PLAN_ID #{plan.id.slice(0, 6)} · {ps.text}</div>
        </div>
        <span className={`plc-status-pill ${ps.cls}`}>{ps.text}</span>
      </div>

      {(plan.status === 'EXECUTING' || plan.status === 'PAUSED') && (
        <div className="plc-progress">
          <div className="plc-progress-label">
            <span className="plc-progress-count">{plan.completedSteps}</span>
            <span className="plc-progress-total">/ {plan.totalSteps} 步已完成</span>
            <span style={{ flex: 1 }} />
            <span className="plc-progress-pct">{progress.toFixed(0)}%</span>
          </div>
          <div className="plc-progress-track">
            <div className="plc-progress-fill" style={{ width: `${progress}%` }} />
          </div>
        </div>
      )}

      <button className="plc-toggle" onClick={() => setExpanded(!expanded)}>
        {expanded ? '▾' : '▸'} {plan.steps?.length || 0} 个步骤
      </button>

      {expanded && plan.steps && (
        <div className="plc-steps">
          <div className="plc-connector" />
          <div className="plc-steps-inner">
            {plan.steps.map((step, i) => (
              <div key={step.id} className="plc-step-wrapper">
                <StepRow step={step} idx={i} />
                {/* Skip button for pending/failed steps */}
                {(step.status === 'PENDING' || step.status === 'FAILED') && !isTerminal && (
                  <button
                    className="plc-skip-btn"
                    onClick={() => onSkipStep(plan.id, step.id)}
                  >
                    跳过
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="plc-footer">
        {plan.status === 'EXECUTING' && (
          <span className="plc-footer-hint">可能继续插入新步骤</span>
        )}
        <span style={{ flex: 1 }} />

        {plan.status === 'AWAITING_APPROVAL' && (
          <>
            <button className="plc-btn primary" onClick={() => onApprove(plan.id)}>
              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
              批准执行
            </button>
            <button className="plc-btn ghost" onClick={() => onCancel(plan.id)}>取消</button>
          </>
        )}
        {plan.status === 'EXECUTING' && (
          <button className="plc-btn ghost" onClick={() => onPause(plan.id)}>
            <svg width="10" height="10" viewBox="0 0 24 24" fill="currentColor"><rect x="6" y="4" width="4" height="16" rx="1"/><rect x="14" y="4" width="4" height="16" rx="1"/></svg>
            暂停
          </button>
        )}
        {plan.status === 'PAUSED' && (
          <>
            <button className="plc-btn primary" onClick={() => onResume(plan.id)}>
              <svg width="10" height="10" viewBox="0 0 24 24" fill="currentColor"><path d="M6 4l14 8-14 8z"/></svg>
              继续
            </button>
            <button className="plc-btn ghost" onClick={() => onCancel(plan.id)}>取消</button>
          </>
        )}
        {(plan.status === 'FAILED' || plan.status === 'PAUSED') && (
          <button className="plc-btn ghost danger" onClick={() => onRollback(plan.id)}>
            <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round"><path d="M3 7v6h6"/><path d="M3 13a9 9 0 1 0 3-7"/></svg>
            回滚
          </button>
        )}
      </div>
    </div>
  );
}
