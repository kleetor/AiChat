import { PanelLeftClose, PanelLeftOpen, User, BookOpen, Brain, FileText, MessageCircle, Users, LogIn, LogOut } from "lucide-react";
import ModelSelector from "@/components/shared/ModelSelector";
import BalanceDisplay from "@/components/shared/BalanceDisplay";
import KBSelector from "@/components/shared/KBSelector";

interface ModelOption {
  id: string;
  name: string;
  tag: string;
  color: string;
}

const toolButtons = [
  { id: "kb", icon: BookOpen, label: "知识库" },
  { id: "memory", icon: Brain, label: "记忆" },
  { id: "prompt", icon: FileText, label: "提示词" },
  { id: "message", icon: MessageCircle, label: "消息" },
  { id: "friend", icon: Users, label: "好友" },
];

interface HeaderProps {
  sidebarOpen: boolean;
  onToggleSidebar: () => void;
  modelOptions: ModelOption[];
  selectedModel: string;
  onSelectModel: (id: string) => void;
  balance: string;
  username: string;
  isLoggedIn: boolean;
  avatarUrl?: string;
  onToolClick: (id: string) => void;
  onBalanceClick?: () => void;
  onProfileClick?: () => void;
  onLogin: () => void;
  onLogout: () => void;
  unreadNotificationCount?: number;
  kbOptions?: { value: string; label: string }[];
  selectedKBId?: string;
  onKBChange?: (value: string) => void;
}

export default function Header({
  sidebarOpen,
  onToggleSidebar,
  modelOptions,
  selectedModel,
  onSelectModel,
  balance,
  username,
  isLoggedIn,
  avatarUrl,
  onToolClick,
  onBalanceClick,
  onProfileClick,
  onLogin,
  onLogout,
  unreadNotificationCount = 0,
  kbOptions,
  selectedKBId,
  onKBChange,
}: HeaderProps) {
  return (
    <header className="flex items-center gap-1 px-2 h-12 shrink-0 border-b border-border relative z-10">
      {/* Sidebar toggle */}
      <button
        onClick={onToggleSidebar}
        className="p-1.5 rounded-md transition-all duration-150 shrink-0 text-muted-foreground hover:bg-accent hover:text-foreground"
      >
        {sidebarOpen ? <PanelLeftClose size={16} /> : <PanelLeftOpen size={16} />}
      </button>

      {/* Model selector */}
      {modelOptions.length > 0 && (
        <ModelSelector options={modelOptions} selected={selectedModel} onSelect={onSelectModel} />
      )}

      {/* KB Selector */}
      {isLoggedIn && kbOptions && onKBChange && (
        <KBSelector value={selectedKBId || ""} options={kbOptions} onChange={onKBChange} />
      )}

      {/* Spacer */}
      <div className="flex-1" />

      {/* Right side */}
      <div className="flex items-center gap-0.5 shrink-0">
        {/* Tool buttons */}
        {isLoggedIn &&
          toolButtons.map((btn) => {
            const Icon = btn.icon;
            return (
              <button
                key={btn.id}
                onClick={() => onToolClick(btn.id)}
                className={`p-1.5 rounded-md transition-all duration-150 text-muted-foreground hover:bg-accent hover:text-foreground relative ${
                  btn.id === "message" && unreadNotificationCount > 0 ? "" : ""
                }`}
                title={btn.label}
              >
                <Icon size={15} />
                {btn.id === "message" && unreadNotificationCount > 0 && (
                  <span className="absolute -top-0.5 -right-0.5 w-3.5 h-3.5 rounded-full bg-destructive text-destructive-foreground text-[8px] flex items-center justify-center font-medium">
                    {unreadNotificationCount > 9 ? "9+" : unreadNotificationCount}
                  </span>
                )}
              </button>
            );
          })}

        {/* Balance */}
        {isLoggedIn && <BalanceDisplay amount={balance} onClick={onBalanceClick} />}

        {/* User avatar */}
        {isLoggedIn && (
          <div
            className={`flex items-center gap-1.5 ml-1 ${onProfileClick ? "cursor-pointer hover:opacity-80" : ""}`}
            onClick={onProfileClick}
            title="查看个人信息"
          >
            {avatarUrl ? (
              <img src={avatarUrl} alt={username} className="w-7 h-7 rounded-full object-cover" />
            ) : (
              <div className="w-7 h-7 rounded-full flex items-center justify-center text-xs font-medium bg-accent text-foreground">
                <User size={13} />
              </div>
            )}
            <span className="text-xs hidden lg:block text-muted-foreground max-w-[80px] truncate">
              {username}
            </span>
          </div>
        )}

        {/* Auth buttons */}
        {isLoggedIn ? (
          <button
            onClick={onLogout}
            className="p-1.5 rounded-md transition-all duration-150 text-muted-foreground hover:bg-destructive/10 hover:text-destructive shrink-0"
            title="退出登录"
          >
            <LogOut size={15} />
          </button>
        ) : (
          <button
            onClick={onLogin}
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-xs font-medium text-muted-foreground hover:bg-accent hover:text-foreground shrink-0"
            title="登录"
          >
            <LogIn size={14} />
            <span className="hidden sm:inline">登录</span>
          </button>
        )}
      </div>
    </header>
  );
}
