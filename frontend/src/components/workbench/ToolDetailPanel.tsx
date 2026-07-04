import type { ReactNode } from 'react';

interface ToolDetailPanelProps {
  title: string;
  subtitle?: string;
  meta?: ReactNode;
  actions?: ReactNode;
  dangerActions?: ReactNode;
  children?: ReactNode;
  empty?: boolean;
  emptyMessage?: string;
}

export default function ToolDetailPanel({
  title,
  subtitle,
  meta,
  actions,
  dangerActions,
  children,
  empty = false,
  emptyMessage = '请选择一项查看详情',
}: ToolDetailPanelProps) {
  return (
    <section className="twb-detail-panel" role="complementary" aria-label={title}>
      <div className="twb-detail-head">
        <h2>{title}</h2>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {meta && <div className="twb-detail-meta">{meta}</div>}
      <div className="twb-detail-content">{empty ? <div className="twb-empty">{emptyMessage}</div> : children}</div>
      {actions && <div className="twb-detail-actions">{actions}</div>}
      {dangerActions && <div className="twb-detail-danger">{dangerActions}</div>}
    </section>
  );
}
