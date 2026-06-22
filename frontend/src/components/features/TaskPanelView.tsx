import { useEffect, useState, useCallback } from 'react';
import {
  getPlans, getPlanDetail, approvePlan, pausePlan, resumePlan,
  cancelPlan, rollbackPlan, skipStep,
  type TaskPlanData,
} from '../../api';
import PlanApprovalCard from '../chat/PlanApprovalCard';

const T = {
  bg: 'var(--color-paper-0, #fbf7ee)',
  surface: 'var(--color-paper-1, #f5efe0)',
  border: 'rgba(74,64,50,0.12)',
  borderStrong: 'rgba(74,64,50,0.22)',
  text: 'var(--color-text, #2b2620)',
  textSecondary: 'var(--color-text-secondary, #7a6b58)',
  accent: 'var(--color-accent, #b8452e)',
  success: '#6b7a5a',
  font: '-apple-system, "PingFang SC", "Noto Sans SC", "Helvetica Neue", sans-serif',
};

export default function TaskPanelView({ onClose }: { onClose: () => void }) {
  const [plans, setPlans] = useState<TaskPlanData[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedPlanId, setSelectedPlanId] = useState<string | null>(null);
  const [detailPlan, setDetailPlan] = useState<TaskPlanData | null>(null);

  const loadPlans = useCallback(async () => {
    try {
      const data = await getPlans();
      setPlans(data);
    } catch { /* ignore */ }
    setLoading(false);
  }, []);

  useEffect(() => { loadPlans(); }, [loadPlans]);

  useEffect(() => {
    if (!selectedPlanId) { setDetailPlan(null); return; }
    getPlanDetail(selectedPlanId).then(setDetailPlan).catch(() => {});
  }, [selectedPlanId]);

  const handleAction = async (action: string, planId: string, stepId?: string) => {
    try {
      switch (action) {
        case 'approve': await approvePlan(planId); break;
        case 'pause': await pausePlan(planId); break;
        case 'resume': await resumePlan(planId); break;
        case 'cancel': await cancelPlan(planId); break;
        case 'rollback': await rollbackPlan(planId); break;
        case 'skipStep': if (stepId) await skipStep(planId, stepId); break;
      }
      // Refresh
      const updated = await getPlanDetail(planId);
      setPlans(prev => prev.map(p => p.id === planId ? updated : p));
      if (selectedPlanId === planId) setDetailPlan(updated);
    } catch (err) {
      console.error('Task action failed:', err);
    }
  };

  const STATUS_COLOR: Record<string, string> = {
    AWAITING_APPROVAL: '#c9a959',
    EXECUTING: T.accent,
    PAUSED: '#c9a959',
    COMPLETED: T.success,
    FAILED: '#c44',
    CANCELLED: T.textSecondary,
    CANCELLED_PARTIAL: T.textSecondary,
  };

  const STATUS_LABEL: Record<string, string> = {
    AWAITING_APPROVAL: '待审批',
    EXECUTING: '执行中',
    PAUSED: '已暂停',
    COMPLETED: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消',
    CANCELLED_PARTIAL: '部分取消',
  };

  return (
    <div style={{
      position: 'fixed', inset: 0, zIndex: 60,
      background: T.bg, fontFamily: T.font, display: 'flex', flexDirection: 'column',
    }}>
      {/* Header */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 12, padding: '14px 24px',
        borderBottom: `1px solid ${T.borderStrong}`, flexShrink: 0,
      }}>
        <span style={{ fontSize: 18, fontWeight: 700, color: T.text, flex: 1 }}>
          任务中心
        </span>
        <button onClick={loadPlans} style={{
          background: T.surface, border: `1px solid ${T.border}`, borderRadius: 8,
          padding: '6px 14px', fontSize: 13, cursor: 'pointer', color: T.textSecondary,
        }}>
          刷新
        </button>
        <button type="button" aria-label="Close task panel" onClick={onClose} style={{
          background: 'none', border: `1px solid ${T.border}`, borderRadius: 8,
          padding: '6px 14px', fontSize: 13, cursor: 'pointer', color: T.textSecondary,
        }}>
          关闭
        </button>
      </div>

      {/* Content */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {/* Plan list */}
        <div style={{
          width: 360, borderRight: `1px solid ${T.border}`,
          overflowY: 'auto', padding: 16,
        }}>
          {loading && <div style={{ color: T.textSecondary, fontSize: 14, padding: 20 }}>加载中...</div>}
          {!loading && plans.length === 0 && (
            <div style={{ color: T.textSecondary, fontSize: 14, padding: 20, textAlign: 'center' }}>
              暂无任务计划
            </div>
          )}
          {plans.map(plan => {
            const isSelected = selectedPlanId === plan.id;
            const statusColor = STATUS_COLOR[plan.status] || T.textSecondary;
            const progress = plan.totalSteps > 0 ? (plan.completedSteps / plan.totalSteps) * 100 : 0;

            return (
              <button key={plan.id} type="button" aria-pressed={isSelected} onClick={() => setSelectedPlanId(plan.id)} style={{
                padding: '12px 14px', borderRadius: 10, cursor: 'pointer', marginBottom: 8,
                background: isSelected ? T.surface : 'transparent',
                border: isSelected ? `1px solid ${T.borderStrong}` : `1px solid transparent`,
                transition: 'all 0.15s',
                width: '100%', textAlign: 'left', fontFamily: T.font,
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                  <span style={{ fontSize: 14, fontWeight: 600, color: T.text, flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {plan.goal}
                  </span>
                  <span style={{
                    fontSize: 10, padding: '2px 6px', borderRadius: 8,
                    background: statusColor + '20', color: statusColor, fontWeight: 600, flexShrink: 0,
                  }}>
                    {STATUS_LABEL[plan.status] || plan.status}
                  </span>
                </div>
                <div style={{ fontSize: 11, color: T.textSecondary, marginBottom: 6 }}>
                  {plan.completedSteps}/{plan.totalSteps} 步 · {new Date(plan.createdAt).toLocaleString()}
                </div>
                {(plan.status === 'EXECUTING' || plan.status === 'PAUSED') && (
                  <div style={{ height: 3, background: T.border, borderRadius: 2, overflow: 'hidden' }}>
                    <div style={{
                      height: '100%', borderRadius: 2, width: `${progress}%`,
                      background: statusColor, transition: 'width 0.3s',
                    }} />
                  </div>
                )}
              </button>
            );
          })}
        </div>

        {/* Plan detail */}
        <div style={{ flex: 1, overflowY: 'auto', padding: 24 }}>
          {!detailPlan ? (
            <div style={{ color: T.textSecondary, fontSize: 14, textAlign: 'center', marginTop: 60 }}>
              选择一个任务计划查看详情
            </div>
          ) : (
            <div>
              <h2 style={{ fontSize: 20, fontWeight: 700, color: T.text, margin: '0 0 8px' }}>
                {detailPlan.goal}
              </h2>
              <div style={{ fontSize: 13, color: T.textSecondary, marginBottom: 16 }}>
                原始请求: {detailPlan.originalQuery}
              </div>
              <PlanApprovalCard
                plan={detailPlan}
                onApprove={(id) => handleAction('approve', id)}
                onCancel={(id) => handleAction('cancel', id)}
                onPause={(id) => handleAction('pause', id)}
                onResume={(id) => handleAction('resume', id)}
                onRollback={(id) => handleAction('rollback', id)}
                onSkipStep={(planId, stepId) => handleAction('skipStep', planId, stepId)}
              />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
