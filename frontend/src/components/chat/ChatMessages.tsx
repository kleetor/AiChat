import { useRef, useEffect, useCallback } from "react";
import { Trash2 } from "lucide-react";

interface Message {
  id: number;
  role: "user" | "assistant" | "system";
  content: string;
  createdAt?: string;
  isLocal?: boolean;
  fileUrl?: string | null;
}

interface ChatMessagesProps {
  messages: Message[];
  isGenerating?: boolean;
  onDeleteMessage?: (id: number) => void;
}

export default function ChatMessages({ messages, isGenerating, onDeleteMessage }: ChatMessagesProps) {
  const bottomRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const isUserScrollingUp = useRef(false);
  const prevFirstMsgId = useRef<number | null>(null);

  const isNearBottom = useCallback(() => {
    const el = containerRef.current;
    if (!el) return true;
    return el.scrollHeight - el.scrollTop - el.clientHeight < 80;
  }, []);

  // 切换会话时重置滚动状态
  const firstMsgId = messages.length > 0 ? messages[0].id : null;
  if (prevFirstMsgId.current !== null && firstMsgId !== prevFirstMsgId.current) {
    isUserScrollingUp.current = false;
  }
  prevFirstMsgId.current = firstMsgId;

  useEffect(() => {
    if (!isUserScrollingUp.current) {
      bottomRef.current?.scrollIntoView({ behavior: "smooth" });
    }
  }, [messages]);

  const handleScroll = useCallback(() => {
    isUserScrollingUp.current = !isNearBottom();
  }, [isNearBottom]);

  if (messages.length === 0) return null;

  return (
    <div
      ref={containerRef}
      onScroll={handleScroll}
      className="flex-1 overflow-y-auto px-4 py-6 space-y-5"
    >
      {messages.map((msg) => (
        <div
          key={msg.id}
          className={`flex group ${msg.role === "user" ? "justify-end" : "justify-start"}`}
        >
          <div
            className={`relative max-w-[80%] rounded-2xl px-4 py-3 text-sm leading-relaxed ${
              msg.role === "user"
                ? "bg-primary text-primary-foreground rounded-br-lg"
                : msg.role === "system"
                  ? "bg-destructive/10 text-destructive rounded-bl-lg"
                  : "bg-card border text-foreground rounded-bl-lg"
            }`}
            style={msg.role !== "user" ? { borderColor: msg.role === "system" ? "transparent" : "hsl(var(--border))" } : undefined}
          >
            <p className="whitespace-pre-wrap break-words">{msg.content}</p>
            {msg.fileUrl && msg.role === "user" && (
              <img
                src={msg.fileUrl}
                alt="上传的图片"
                className="mt-2 max-w-full rounded-lg max-h-64 object-contain"
              />
            )}
            {msg.createdAt && (
              <span className="block text-[10px] mt-1.5 opacity-50">
                {new Date(msg.createdAt).toLocaleString()}
              </span>
            )}
            {onDeleteMessage && msg.isLocal && (
              <button
                onClick={() => onDeleteMessage(msg.id)}
                className="absolute -top-2 -right-2 p-0.5 rounded bg-background border opacity-0 group-hover:opacity-100 transition-opacity text-muted-foreground hover:text-destructive"
                style={{ borderColor: "hsl(var(--border))" }}
              >
                <Trash2 size={11} />
              </button>
            )}
          </div>
        </div>
      ))}
      {isGenerating && messages[messages.length - 1]?.role !== "assistant" && (
        <div className="flex justify-start">
          <div className="bg-card border rounded-2xl rounded-bl-lg px-4 py-3" style={{ borderColor: "hsl(var(--border))" }}>
            <div className="flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 rounded-full bg-primary animate-bounce" style={{ animationDelay: "0ms" }} />
              <span className="w-1.5 h-1.5 rounded-full bg-primary animate-bounce" style={{ animationDelay: "150ms" }} />
              <span className="w-1.5 h-1.5 rounded-full bg-primary animate-bounce" style={{ animationDelay: "300ms" }} />
            </div>
          </div>
        </div>
      )}
      <div ref={bottomRef} />
    </div>
  );
}
