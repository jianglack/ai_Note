import { useEffect, type ReactNode } from 'react';
import './ToolWorkbenchShell.css';

export interface ToolAction {
  key: string;
  label: string;
  icon?: ReactNode;
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  disabled?: boolean;
  onClick: () => void;
}

interface ToolWorkbenchShellProps {
  title: string;
  subtitle?: string;
  icon?: ReactNode;
  chips?: ReactNode;
  primaryActions?: ToolAction[];
  secondaryActions?: ToolAction[];
  leftRail?: ReactNode;
  detailPanel?: ReactNode;
  children: ReactNode;
  onBack: () => void;
  backLabel?: string;
  className?: string;
  mainClassName?: string;
  closeOnEscape?: boolean;
}

function renderAction(action: ToolAction) {
  return (
    <button
      key={action.key}
      type="button"
      className={`twb-action twb-action-${action.variant || 'secondary'}`}
      disabled={action.disabled}
      onClick={action.onClick}
      aria-label={action.label}
    >
      {action.icon && <span className="twb-action-icon">{action.icon}</span>}
      <span>{action.label}</span>
    </button>
  );
}

export default function ToolWorkbenchShell({
  title,
  subtitle,
  icon,
  chips,
  primaryActions = [],
  secondaryActions = [],
  leftRail,
  detailPanel,
  children,
  onBack,
  backLabel,
  className,
  mainClassName,
  closeOnEscape = false,
}: ToolWorkbenchShellProps) {
  useEffect(() => {
    if (!closeOnEscape) {
      return undefined;
    }

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onBack();
      }
    };

    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [closeOnEscape, onBack]);

  return (
    <section className={`twb-shell ${className || ''}`} role="dialog" aria-modal="true" aria-label={title}>
      <header className="twb-topbar">
        <div className="twb-title-group">
          <button type="button" className="twb-back" aria-label={backLabel || `关闭${title}`} onClick={onBack}>
            <span aria-hidden="true">‹</span>
          </button>
          {icon && <span className="twb-title-icon">{icon}</span>}
          <div className="twb-title-copy">
            <h1>{title}</h1>
            {subtitle && <p>{subtitle}</p>}
          </div>
          {chips && <div className="twb-chips">{chips}</div>}
        </div>
        <div className="twb-actions">
          {secondaryActions.map(renderAction)}
          {primaryActions.map(renderAction)}
        </div>
      </header>
      <div className={`twb-body ${leftRail ? 'twb-has-rail' : ''} ${detailPanel ? 'twb-has-detail' : ''}`}>
        {leftRail && <aside className="twb-rail">{leftRail}</aside>}
        <main className={`twb-main ${mainClassName || ''}`}>{children}</main>
        {detailPanel && <aside className="twb-detail">{detailPanel}</aside>}
      </div>
    </section>
  );
}
