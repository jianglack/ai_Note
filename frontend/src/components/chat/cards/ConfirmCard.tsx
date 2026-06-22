import './ai-cards.css';

interface Props {
  title: string;
  description: string;
  confirmLabel?: string;
  collapsed?: boolean;
  result?: string;
  onConfirm: () => void;
  onCancel: () => void;
}

export default function ConfirmCard({
  title, description, confirmLabel = '确认删除',
  collapsed, result, onConfirm, onCancel,
}: Props) {
  if (collapsed) {
    return (
      <div className="confirm-card collapsed">
        <div className="confirm-row">
          <div className="confirm-ico">✓</div>
          <div className="confirm-text">
            <div className="confirm-title">{result || '已处理'}</div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="confirm-card">
      <div className="confirm-row">
        <div className="confirm-ico">⚠</div>
        <div className="confirm-text">
          <div className="confirm-title">{title}</div>
          <div className="confirm-sub">{description}</div>
        </div>
      </div>
      <div className="confirm-actions">
        <button className="cc-btn subtle" onClick={onCancel}>取消</button>
        <button className="cc-btn danger" onClick={onConfirm}>{confirmLabel}</button>
      </div>
    </div>
  );
}
