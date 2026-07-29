import { useState } from "react";
import { Plus, Info, X, Sparkles } from "lucide-react";
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
  const [showAbout, setShowAbout] = useState(false);

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

        {/* Footer */}
        <div className="px-3 py-2 border-t shrink-0 space-y-1" style={{ borderColor: "hsl(var(--border))" }}>
          <button
            onClick={() => window.open("/workshop", "_blank")}
            className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-xs text-muted-foreground hover:bg-accent hover:text-foreground transition-colors"
          >
            <Sparkles size={14} />
            提示词社区
          </button>
          <button
            onClick={() => setShowAbout(true)}
            className="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-xs text-muted-foreground hover:bg-accent hover:text-foreground transition-colors"
          >
            <Info size={14} />
            关于本站
          </button>
        </div>

        {/* About Modal */}
        {showAbout && (
          <div className="fixed inset-0 z-50 flex items-center justify-center">
            <div className="fixed inset-0 bg-black/40" onClick={() => setShowAbout(false)} />
            <div className="relative bg-background rounded-2xl shadow-2xl w-full max-w-[400px] mx-4 border border-border p-6">
              <div className="flex items-center justify-between mb-4">
                <h2 className="text-base font-semibold flex items-center gap-2">
                  <Info size={16} /> 关于本站
                </h2>
                <button onClick={() => setShowAbout(false)} className="p-1 rounded-md text-muted-foreground hover:bg-accent hover:text-foreground">
                  <X size={16} />
                </button>
              </div>
              <div className="space-y-4 text-sm text-muted-foreground leading-relaxed">
                <p>
                  本项目为开源项目，源代码托管于 GitHub：
                </p>
                <a
                  href="https://github.com/kleetor/HanaChat-ai-chat-web-student-wip/tree/master"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-foreground underline hover:opacity-70 transition-opacity"
                >
                  github.com/kleetor/HanaChat
                </a>
                <hr style={{ borderColor: "hsl(var(--border))" }} />
                <p className="text-sm text-muted-foreground leading-relaxed">
                  免责声明：本网站仅用于代码功能展示与技术交流，不提供任何商业服务。网站中所展示的支付、赞助等功能均为技术演示，不涉及真实资金交易。请勿将其用于任何违法违规用途。
                </p>
                <hr style={{ borderColor: "hsl(var(--border))" }} />
                <p className="text-sm text-muted-foreground leading-relaxed">
                  数据隐私声明：请勿在对话中发送个人隐私信息（如身份证号、银行卡号、密码等）给AI模型。本网站存储的用户聊天记录仅用于对话功能实现，不会用于其他目的。用户可随时通过删除按钮清除聊天记录。
                </p>
                <hr style={{ borderColor: "hsl(var(--border))" }} />
                <p className="text-sm text-muted-foreground leading-relaxed">
                  联系方式：<a href="mailto:1405921723@qq.com" className="text-foreground underline hover:opacity-70 transition-opacity">1405921723@qq.com</a>
                </p>
              </div>
            </div>
          </div>
        )}

        <style>{`
          .scrollbar-none::-webkit-scrollbar { display: none; }
          .scrollbar-none { scrollbar-width: none; }
        `}</style>
      </aside>
    </>
  );
}
