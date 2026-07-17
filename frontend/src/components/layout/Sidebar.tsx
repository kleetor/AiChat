import { Plus } from "lucide-react";
import ConversationList from "@/components/chat/ConversationList";
import BrandIcon from "@/components/shared/BrandIcon";

interface Conversation {
  id: number;
  title: string;
  preview: string;
  time: string;
  active: boolean;
}

interface SidebarProps {
  open: boolean;
  conversations: Conversation[];
  activeConv: number;
  onNewChat: () => void;
  onSelectConv: (id: number) => void;
  onDeleteConv?: (id: number) => void;
  onClose?: () => void;
}

export default function Sidebar({
  open,
  conversations,
  activeConv,
  onNewChat,
  onSelectConv,
  onDeleteConv,
  onClose,
}: SidebarProps) {
  const handleSelectConv = (id: number) => {
    onSelectConv(id);
    onClose?.();
  };

  return (
    <>
      {/* Mobile backdrop */}
      {open && (
        <div
          className="fixed inset-0 bg-black/40 z-30 md:hidden"
          onClick={onClose}
        />
      )}

      <aside
        className="flex flex-col overflow-hidden transition-all duration-300 ease-in-out border-r fixed inset-y-0 left-0 z-40 md:relative md:z-auto md:shrink-0"
        style={{
          width: open ? 240 : 0,
          opacity: open ? 1 : 0,
          borderColor: "hsl(var(--border))",
          background: "hsl(var(--sidebar))",
        }}
      >
        {/* Sidebar header */}
        <div className="flex items-center justify-between px-4 py-4 shrink-0">
          <div className="flex items-center gap-2">
            <BrandIcon size={28} className="rounded-lg" />
            <span className="text-sm font-semibold tracking-tight text-foreground">HanaChat</span>
          </div>
        </div>

        {/* New chat button */}
        <div className="px-3 pb-3 shrink-0">
          <button
            onClick={() => { onNewChat(); onClose?.(); }}
            className="w-full flex items-center gap-2.5 px-3 py-2.5 rounded-lg text-sm font-medium transition-all duration-150 bg-primary text-primary-foreground hover:opacity-90 active:scale-[0.98]"
          >
            <Plus size={15} strokeWidth={2.5} />
            新建对话
          </button>
        </div>

        {/* Conversation list */}
        <ConversationList conversations={conversations} activeId={activeConv} onSelect={handleSelectConv} onDelete={onDeleteConv} />

        <style>{`
          .scrollbar-none::-webkit-scrollbar { display: none; }
          .scrollbar-none { scrollbar-width: none; }
        `}</style>
      </aside>
    </>
  );
}
