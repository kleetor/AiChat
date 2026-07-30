import { useState, useCallback, useRef } from "react";
import {
  getConversations,
  createConversation,
  deleteConversation,
  type Conversation,
  type ChatMessage,
} from "@/lib/services";

export function useConversations() {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeConvId, setActiveConvId] = useState<number | null>(null);
  const loadSeqRef = useRef(0);

  const loadConversations = useCallback(async () => {
    try {
      const data = await getConversations();
      setConversations(data);
    } catch (e) {
      console.warn("加载会话列表失败:", e);
    }
  }, []);

  const handleNewChat = useCallback(async (title?: string, promptId?: number | null) => {
    try {
      const conv = await createConversation(title, promptId);
      setConversations(prev => [conv, ...prev]);
      setActiveConvId(conv.id);
      return conv;
    } catch (e) {
      console.warn("创建会话失败:", e);
      throw e;
    }
  }, []);

  const handleSelectConv = useCallback(async (id: number, loadHistory: (id: number) => Promise<ChatMessage[]>) => {
    setActiveConvId(id);
    const seq = ++loadSeqRef.current;
    try {
      const result = await loadHistory(id);
      // 忽略过期响应：只更新当前选中的会话
      if (seq === loadSeqRef.current) {
        return result;
      }
      return null;
    } catch (e) {
      console.warn("加载历史消息失败:", e);
      return null;
    }
  }, []);

  const handleDeleteConv = useCallback(async (id: number) => {
    try {
      await deleteConversation(id);
      setConversations(prev => prev.filter(c => c.id !== id));
    } catch (e) {
      console.warn("删除会话失败:", e);
    }
    return id;
  }, []);

  return {
    conversations,
    activeConvId,
    setActiveConvId,
    loadConversations,
    handleNewChat,
    handleSelectConv,
    handleDeleteConv,
  };
}
