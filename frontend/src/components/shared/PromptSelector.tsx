import { useState, useRef, useEffect } from "react";
import { ChevronDown } from "lucide-react";
import type { Prompt } from "@/lib/services";

interface PromptSelectorProps {
  prompts: Prompt[];
  value: number | null;
  onChange: (promptId: number | null) => void;
  placeholder?: string;
  className?: string;
}

export default function PromptSelector({
  prompts,
  value,
  onChange,
  placeholder = "无（共享会话）",
  className = "",
}: PromptSelectorProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const selectedPrompt = prompts.find((p) => p.id === value);
  const displayText = selectedPrompt ? selectedPrompt.name : placeholder;

  // 点击外部关闭
  useEffect(() => {
    const handleClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    if (open) document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [open]);

  return (
    <div ref={ref} className={`relative ${className}`}>
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="w-full flex items-center justify-between px-3 py-2 rounded-lg border bg-transparent text-xs text-left outline-none transition-colors hover:border-primary/50"
        style={{ borderColor: open ? "hsl(var(--primary))" : "hsl(var(--border))" }}
      >
        <span className="truncate">{displayText}</span>
        <ChevronDown
          size={14}
          className={`shrink-0 text-muted-foreground transition-transform ${open ? "rotate-180" : ""}`}
        />
      </button>
      {open && (
        <div
          className="absolute top-full left-0 right-0 z-50 mt-1 max-h-[200px] overflow-y-auto rounded-lg border bg-background shadow-lg"
          style={{ borderColor: "hsl(var(--border))", scrollbarWidth: "thin" }}
        >
          <div
            className={`px-3 py-2 text-xs cursor-pointer hover:bg-accent ${value === null ? "bg-primary/10 text-primary font-medium" : "text-muted-foreground"}`}
            onClick={() => { onChange(null); setOpen(false); }}
          >
            {placeholder}
          </div>
          {prompts.map((p) => (
            <div
              key={p.id}
              className={`px-3 py-2 text-xs cursor-pointer hover:bg-accent ${value === p.id ? "bg-primary/10 text-primary font-medium" : "text-foreground"}`}
              onClick={() => { onChange(p.id); setOpen(false); }}
            >
              {p.name}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
