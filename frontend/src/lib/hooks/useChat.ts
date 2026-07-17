import { useRef, useCallback, useState } from "react";
import {
  streamMessage,
  createConversation,
  type ChatMessage,
  type ChatRequest,
} from "@/lib/services";

export function useChat(onError?: (msg: string) => void) {
  const [isGenerating, setIsGenerating] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const sendingRef = useRef(false);

  /**
   * 发送消息并处理 SSE 流式响应。
   * 返回 assistant 消息的最终 content，失败时返回 null。
   */
  const handleSend = useCallback(async (
    req: ChatRequest,
    activeConvId: number | null,
    setActiveConvId: (id: number) => void,
    setMessages: React.Dispatch<React.SetStateAction<ChatMessage[]>>,
    setConversations: React.Dispatch<React.SetStateAction<{ id: number; title: string }[]>>,
    inputContent: string,
  ) => {
    if (sendingRef.current) return;
    // 中止上一个未完成的请求，防止旧流数据覆盖新会话
    if (abortRef.current) {
      abortRef.current.abort();
      abortRef.current = null;
    }
    sendingRef.current = true;

    let convId = activeConvId;
    if (!convId) {
      try {
        const conv = await createConversation();
        setConversations(prev => [{ id: conv.id, title: "新建对话", updatedAt: new Date().toISOString(), createdAt: new Date().toISOString(), userId: 0 }, ...prev]);
        convId = conv.id;
        setActiveConvId(convId);
      } catch {
        onError?.("创建会话失败，请稍后重试");
        sendingRef.current = false;
        return;
      }
    }

    const userMsg: ChatMessage = {
      id: Date.now(),
      role: "user",
      content: inputContent,
      createdAt: new Date().toISOString(),
      isLocal: true,
    };
    setMessages(prev => [...prev, userMsg]);
    setIsGenerating(true);

    try {
      const { controller, stream } = streamMessage(convId, req);
      abortRef.current = controller;

      let fullReply = "";
      let assistantMsgId = 0;

      for await (const chunk of stream) {
        if (!assistantMsgId) {
          assistantMsgId = Date.now() + 1;
        }
        fullReply += chunk;
        setMessages(prev => {
          const exists = prev.some(m => m.id === assistantMsgId);
          if (!exists) {
            return [...prev, { id: assistantMsgId, role: "assistant" as const, content: fullReply, isLocal: true }];
          }
          return prev.map(m => (m.id === assistantMsgId ? { ...m, content: fullReply } : m));
        });
      }

      if (assistantMsgId) {
        setMessages(prev =>
          prev.map(m =>
            m.id === assistantMsgId ? { ...m, content: fullReply, createdAt: new Date().toISOString() } : m,
          ),
        );
      }
      return fullReply;
    } catch (e) {
      // 忽略主动取消的错误，不显示错误提示
      if (e instanceof DOMException && e.name === "AbortError") return null;
      onError?.("请求失败，请稍后重试");
      setMessages(prev => [
        ...prev,
        {
          id: Date.now() + 2,
          role: "system",
          content: "请求失败，请稍后重试。",
          isLocal: true,
        },
      ]);
      return null;
    } finally {
      setIsGenerating(false);
      abortRef.current = null;
      sendingRef.current = false;
    }
  }, [onError]);

  const handleStop = useCallback(() => {
    if (abortRef.current) {
      abortRef.current.abort();
      abortRef.current = null;
    }
    setIsGenerating(false);
    sendingRef.current = false;
  }, []);

  return { isGenerating, handleSend, handleStop };
}
