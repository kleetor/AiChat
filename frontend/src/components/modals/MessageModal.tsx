import { X, MessageCircle, Trash2 } from "lucide-react";
import type { Notification } from "@/lib/services";

interface MessageModalProps {
  open: boolean;
  onClose: () => void;
  messages: Notification[];
  onMarkAllRead: () => void;
  onMarkOneRead: (id: number) => void;
  onDelete: (id: number) => void;
}

export default function MessageModal({ open, onClose, messages, onMarkAllRead, onMarkOneRead, onDelete }: MessageModalProps) {
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="fixed inset-0 bg-black/40" onClick={onClose} />
      <div className="relative bg-background rounded-2xl shadow-2xl w-full max-w-[420px] max-h-[70vh] overflow-hidden mx-4 border border-border flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-border shrink-0">
          <h2 className="text-base font-semibold flex items-center gap-2">
            <MessageCircle size={16} /> 消息
          </h2>
          <div className="flex items-center gap-2">
            <button
              onClick={onMarkAllRead}
              className="text-[11px] text-muted-foreground hover:text-foreground transition-colors"
            >
              全部已读
            </button>
            <button onClick={onClose} className="p-1 rounded-md text-muted-foreground hover:bg-accent hover:text-foreground">
              <X size={16} />
            </button>
          </div>
        </div>

        {/* Message list */}
        <div className="flex-1 overflow-y-auto p-3 space-y-1">
          {messages.length === 0 ? (
            <p className="text-[11px] text-muted-foreground/60 text-center py-8">暂无消息</p>
          ) : (
            messages.map((msg) => (
              <div
                key={msg.id}
                onClick={() => { if (!msg.isRead) onMarkOneRead(msg.id); }}
                className={`relative group p-3 pr-8 rounded-lg transition-colors cursor-pointer ${msg.isRead ? "" : "bg-accent/60"}`}
              >
                <div className="flex items-start justify-between gap-2">
                  <p className="text-xs font-medium text-foreground">{msg.title}</p>
                  <span className="text-[10px] text-muted-foreground shrink-0">
                    {msg.createdAt ? new Date(msg.createdAt).toLocaleString() : ""}
                  </span>
                </div>
                <p className="text-[11px] text-muted-foreground mt-0.5 line-clamp-2">{msg.content}</p>
                <button
                  onClick={(e) => { e.stopPropagation(); onDelete(msg.id); }}
                  className="absolute top-2 right-2 w-5 h-5 rounded-full flex items-center justify-center opacity-0 group-hover:opacity-100 hover:bg-destructive/15 hover:text-destructive text-muted-foreground transition-all"
                  title="删除"
                >
                  <Trash2 size={12} />
                </button>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
