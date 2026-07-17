import { X } from "lucide-react";

interface PromptIndicatorProps {
  promptName: string | null;
  onRemove: () => void;
  onClick: () => void;
}

export default function PromptIndicator({ promptName, onRemove, onClick }: PromptIndicatorProps) {
  if (!promptName) return null;

  return (
    <span className="flex items-center gap-1 px-2 py-1 rounded-md text-[11px] bg-primary/10 text-primary border border-primary/20 shrink-0 max-w-[120px]">
      <span className="truncate cursor-pointer" onClick={onClick}>{promptName}</span>
      <button
        onClick={(e) => { e.stopPropagation(); onRemove(); }}
        className="shrink-0 hover:text-destructive transition-colors"
      >
        <X size={11} strokeWidth={2.5} />
      </button>
    </span>
  );
}
