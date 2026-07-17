import { useState, useCallback } from "react";
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
  deleteNotification as deleteNotif,
  type Notification,
} from "@/lib/services";

export function useNotifications() {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);

  const loadNotifications = useCallback(async () => {
    try {
      const data = await getNotifications();
      setNotifications(data);
    } catch (e) {
      console.warn("加载通知失败:", e);
    }
  }, []);

  const loadUnreadCount = useCallback(async () => {
    try {
      const { count } = await getUnreadNotificationCount();
      setUnreadCount(count);
    } catch (e) {
      console.warn("加载未读通知数失败:", e);
    }
  }, []);

  const handleMarkAllRead = useCallback(async () => {
    try {
      await markAllNotificationsRead();
      setNotifications(prev => prev.map(n => ({ ...n, isRead: true })));
      setUnreadCount(0);
    } catch (e) {
      console.warn("全部标记已读失败:", e);
    }
  }, []);

  const handleMarkOneRead = useCallback(async (id: number) => {
    try {
      await markNotificationRead(id);
      setNotifications(prev => prev.map(n => n.id === id ? { ...n, isRead: true } : n));
      setUnreadCount(prev => Math.max(0, prev - 1));
    } catch (e) {
      console.warn("标记已读失败:", e);
    }
  }, []);

  const handleDeleteNotification = useCallback(async (id: number) => {
    try {
      await deleteNotif(id);
      setNotifications(prev => {
        const item = prev.find(n => n.id === id);
        if (item && !item.isRead) {
          setUnreadCount(prev => Math.max(0, prev - 1));
        }
        return prev.filter(n => n.id !== id);
      });
    } catch (e) {
      console.warn("删除通知失败:", e);
    }
  }, []);

  return {
    notifications,
    unreadCount,
    loadNotifications,
    loadUnreadCount,
    handleMarkAllRead,
    handleMarkOneRead,
    handleDeleteNotification,
  };
}
