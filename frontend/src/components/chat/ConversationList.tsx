import { MoreHorizontal, Trash2 } from "lucide-react";
import { useState } from "react";

interface Conversation {
  id: number;
  title: string;
  preview: string;
  time: string;
  active: boolean;
}

interface ConversationListProps {
  conversations: Conversation[];
  activeId: number;
  onSelect: (id: number) => void;
  onDelete?: (id: number) => void;
}

export default function ConversationList({ conversations, activeId, onSelect, onDelete }: ConversationListProps) {
  const [confirmDelete, setConfirmDelete] = useState<number | null>(null);

  return (
    <div className="flex-1 overflow-y-auto px-2 space-y-0.5 scrollbar-none">
      <p className="px-2 pt-3 pb-1.5 text-[10px] uppercase tracking-widest font-medium text-muted-foreground">
        最近对话
      </p>
      {conversations.map((conv) => (
        <div
          key={conv.id}
          role="button"
          tabIndex={0}
          onClick={() => onSelect(conv.id)}
          onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') onSelect(conv.id); }}
          className={`group w-full text-left px-2.5 py-2.5 rounded-lg transition-all duration-150 relative cursor-pointer ${
            activeId === conv.id
              ? "bg-sidebar-accent text-foreground"
              : "text-muted-foreground hover:bg-accent"
          }`}
        >
          <div className="text-xs font-medium truncate leading-snug">{conv.title}</div>
          <div className="text-[11px] truncate mt-0.5 leading-relaxed text-muted-foreground/70">
            {conv.time}
          </div>
          {onDelete && (
            <div className="absolute right-2 top-1/2 -translate-y-1/2 opacity-0 group-hover:opacity-100 transition-opacity">
              {confirmDelete === conv.id ? (
                <button
                  className="p-1 rounded text-destructive hover:bg-destructive/10"
                  onClick={(e) => {
                    e.stopPropagation();
                    onDelete(conv.id);
                    setConfirmDelete(null);
                  }}
                >
                  <Trash2 size={13} />
                </button>
              ) : (
                <button
                  className="p-1 rounded text-muted-foreground hover:text-foreground"
                  onClick={(e) => {
                    e.stopPropagation();
                    setConfirmDelete(conv.id);
                  }}
                >
                  <MoreHorizontal size={13} />
                </button>
              )}
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
