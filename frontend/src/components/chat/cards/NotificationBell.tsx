import './ai-cards.css';

export interface NotificationItem {
  id: string;
  kind: string;
  icon: string;
  iconBg: string;
  iconColor: string;
  title: string;
  meta: string;
  read: boolean;
  primaryAction?: { label: string; onClick: () => void };
}

interface Props {
  unreadCount: number;
  notifications: NotificationItem[];
  onMarkAllRead: () => void;
  onClose: () => void;
  onViewAll: () => void;
  onAction: (id: string) => void;
}

function MoAvatar() {
  return (
    <span style={{
      width: 26, height: 26, borderRadius: 7, background: '#2a1d16',
      color: '#fff8ec', fontWeight: 700, fontSize: 13,
      display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
      flexShrink: 0, fontFamily: "'Noto Serif SC', serif",
    }}>墨</span>
  );
}

export default function NotificationBell({
  unreadCount, notifications, onMarkAllRead, onClose, onViewAll, onAction,
}: Props) {
  return (
    <div className="bell-host">
      <div className="bell-top">
        <div className="bell-id">
          <MoAvatar />
          <div>
            <div className="bell-id-name">墨子 · AI 助手</div>
            <div className="bell-id-sub">已读全部 {notifications.length} 条</div>
          </div>
        </div>
        <div className="bell-actions">
          <button className="bell-btn active">
            🔔
            {unreadCount > 0 && <span className="bell-badge">{unreadCount}</span>}
          </button>
          <button className="bell-btn" onClick={onClose}>×</button>
        </div>
      </div>
      <div className="bell-listhead">
        <span className="bell-h-title">通知 <span className="bell-h-num">{unreadCount}</span></span>
        <button className="cc-link-btn" onClick={onMarkAllRead}>全部已读</button>
      </div>
      {notifications.map(n => (
        <div key={n.id} className={`bell-row ${n.read ? 'read' : 'unread'}`} style={{ paddingLeft: 18 }}>
          <div className="bell-row-ico" style={{ background: n.iconBg, color: n.iconColor }}>{n.icon}</div>
          <div className="bell-row-body">
            <div className="bell-row-t">{n.title}</div>
            <div className="bell-row-m">{n.meta}</div>
          </div>
          {n.primaryAction ? (
            <button className={`cc-btn ${n.read ? 'ghost' : 'primary'} tiny`} onClick={() => onAction(n.id)}>
              {n.primaryAction.label}
            </button>
          ) : (
            <button className="cc-btn ghost tiny" onClick={() => onAction(n.id)}>查看</button>
          )}
        </div>
      ))}
      <div className="bell-foot">
        <span>仅显示最近 7 天</span>
        <button className="cc-link-btn" style={{ color: 'var(--color-accent, #b8452e)' }} onClick={onViewAll}>查看全部 →</button>
      </div>
    </div>
  );
}
