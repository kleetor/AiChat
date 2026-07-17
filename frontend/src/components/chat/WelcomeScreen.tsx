import { Sparkles, Zap, MessageSquare, LayoutGrid, type LucideIcon } from "lucide-react";

interface QuickAction {
  icon: LucideIcon;
  label: string;
  desc: string;
}

const quickActions: QuickAction[] = [
  { icon: Sparkles, label: "创意写作", desc: "帮我写一篇关于..." },
  { icon: Zap, label: "代码助手", desc: "分析并优化这段代码..." },
  { icon: MessageSquare, label: "知识问答", desc: "解释一个复杂概念..." },
  { icon: LayoutGrid, label: "数据分析", desc: "分析这份数据集..." },
];

interface WelcomeScreenProps {
  onActionClick: (desc: string) => void;
}

function QuickActionCard({ action, onClick }: { action: QuickAction; onClick: () => void }) {
  const Icon = action.icon;
  return (
    <button
      className="group flex items-start gap-3 p-4 rounded-xl text-left transition-all duration-200 bg-card border hover:border-primary/35 hover:bg-accent"
      onClick={onClick}
    >
      <div className="w-7 h-7 rounded-lg flex items-center justify-center shrink-0 mt-0.5 bg-primary/15">
        <Icon size={14} className="text-primary" />
      </div>
      <div>
        <div className="text-xs font-medium mb-1 text-foreground">{action.label}</div>
        <div className="text-[11px] leading-relaxed text-muted-foreground">{action.desc}</div>
      </div>
    </button>
  );
}

export default function WelcomeScreen({ onActionClick }: WelcomeScreenProps) {
  return (
    <div className="flex flex-col items-center justify-center h-full min-h-[400px] px-6 py-12">

      <h1 className="text-2xl font-semibold mb-2 tracking-tight text-foreground">
        欢迎使用 HanaChat
      </h1>
      <p className="text-sm text-center max-w-sm leading-relaxed mb-10 text-muted-foreground">
        选择左侧会话继续对话，或新建一个会话开始探索
      </p>

      {/* Quick action cards */}
      <div className="grid grid-cols-2 gap-3 w-full max-w-lg">
        {quickActions.map((action) => (
          <QuickActionCard key={action.label} action={action} onClick={() => onActionClick(action.desc)} />
        ))}
      </div>
    </div>
  );
}

export { quickActions };
