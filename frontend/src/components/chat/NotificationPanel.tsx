import { useState, useEffect, useCallback } from 'react';
import NotificationBell from './cards/NotificationBell';
import type { NotificationItem } from './cards/NotificationBell';
import { getNotifications, markNotificationRead, markAllNotificationsRead } from '../../api';

interface Props {
  onClose: () => void;
  onAction?: (notificationId: string) => void;
}

const kindConfig: Record<string, { icon: string; iconBg: string; iconColor: string; actionLabel: string }> = {
  OVERDUE_SCHEDULE: { icon: '⏰', iconBg: 'rgba(200,137,58,0.12)', iconColor: '#c8893a', actionLabel: '处理' },
  UPCOMING_SCHEDULE: { icon: '📅', iconBg: 'rgba(107,163,104,0.12)', iconColor: '#6b7a5a', actionLabel: '查看' },
  ORGANIZE_NOTES: { icon: '✦', iconBg: 'rgba(184,69,46,0.1)', iconColor: '#b8452e', actionLabel: '处理' },
  REVIEW_NOTE: { icon: '📖', iconBg: 'rgba(245,227,211,0.5)', iconColor: '#6b5848', actionLabel: '查看' },
};

export default function NotificationPanel({ onClose, onAction }: Props) {
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [loading, setLoading] = useState(true);

  const loadNotifications = useCallback(async () => {
    try {
      const data = await getNotifications();
      const items: NotificationItem[] = data.map(n => {
        const cfg = kindConfig[n.type] || kindConfig.REVIEW_NOTE;
        return {
          id: n.id,
          kind: n.type,
          icon: cfg.icon,
          iconBg: cfg.iconBg,
          iconColor: cfg.iconColor,
          title: n.title,
          meta: formatTimeAgo(n.createdAt),
          read: n.isRead,
          primaryAction: { label: cfg.actionLabel, onClick: () => onAction?.(n.id) },
        };
      });
      setNotifications(items);
    } catch (err) {
      console.error('Failed to load notifications:', err);
    } finally {
      setLoading(false);
    }
  }, [onAction]);

  useEffect(() => { loadNotifications(); }, [loadNotifications]);

  const unreadCount = notifications.filter(n => !n.read).length;

  const handleMarkAllRead = async () => {
    try {
      await markAllNotificationsRead();
      setNotifications(prev => prev.map(n => ({ ...n, read: true })));
    } catch (err) {
      console.error('Failed to mark all read:', err);
    }
  };

  const handleAction = async (id: string) => {
    try {
      await markNotificationRead(id);
      setNotifications(prev => prev.map(n => n.id === id ? { ...n, read: true } : n));
      onAction?.(id);
    } catch (err) {
      console.error('Failed to handle notification:', err);
    }
  };

  if (loading) {
    return (
      <div style={{ padding: 20, textAlign: 'center', color: '#9a8770', fontSize: 13 }}>
        加载通知中...
      </div>
    );
  }

  return (
    <NotificationBell
      unreadCount={unreadCount}
      notifications={notifications}
      onMarkAllRead={handleMarkAllRead}
      onClose={onClose}
      onViewAll={() => {}}
      onAction={handleAction}
    />
  );
}

function formatTimeAgo(dateStr: string): string {
  const now = Date.now();
  const then = new Date(dateStr).getTime();
  const diffMs = now - then;
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1) return '刚刚';
  if (diffMin < 60) return `${diffMin} 分钟前`;
  const diffHours = Math.floor(diffMin / 60);
  if (diffHours < 24) return `${diffHours} 小时前`;
  const diffDays = Math.floor(diffHours / 24);
  if (diffDays < 7) return `${diffDays} 天前`;
  return '一周前';
}
