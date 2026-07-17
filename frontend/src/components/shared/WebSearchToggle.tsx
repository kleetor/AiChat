import { Globe } from "lucide-react";

interface WebSearchToggleProps {
  enabled: boolean;
  onChange: (enabled: boolean) => void;
}

export default function WebSearchToggle({ enabled, onChange }: WebSearchToggleProps) {
  return (
    <button
      onClick={() => onChange(!enabled)}
      className={`p-1.5 rounded-md transition-all duration-150 shrink-0 ${
        enabled
          ? "text-primary bg-primary/10"
          : "text-muted-foreground hover:bg-accent hover:text-foreground"
      }`}
      title={enabled ? "关闭联网搜索" : "开启联网搜索"}
    >
      <Globe size={16} />
    </button>
  );
}
