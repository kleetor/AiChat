import { useState, useRef, useEffect } from "react";
import { ChevronDown } from "lucide-react";

interface ModelOption {
  id: string;
  name: string;
  tag: string;
  color: string;
}

interface ModelSelectorProps {
  options: ModelOption[];
  selected: string;
  onSelect: (id: string) => void;
}

export default function ModelSelector({ options, selected, onSelect }: ModelSelectorProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const current = options.find((m) => m.id === selected) || options[0];
  if (!current) return null;

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, []);

  return (
    <div className="relative" ref={ref}>
      <button
        onClick={() => setOpen(!open)}
        className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium transition-all duration-150 bg-accent text-foreground"
      >
        <span className="w-1.5 h-1.5 rounded-full" style={{ background: current.color }} />
        {current.name}
        <ChevronDown size={12} className="text-muted-foreground" />
      </button>

      {open && (
        <div className="absolute top-full left-0 mt-1.5 rounded-xl overflow-hidden z-50 w-44 py-1 bg-popover border shadow-[0_8px_24px_rgba(0,0,0,0.1)]">
          {options.map((m) => (
            <button
              key={m.id}
              onClick={() => {
                onSelect(m.id);
                setOpen(false);
              }}
              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs transition-all duration-100 ${
                selected === m.id ? "bg-accent text-foreground" : "text-muted-foreground hover:bg-accent"
              }`}
            >
              <span className="w-1.5 h-1.5 rounded-full shrink-0" style={{ background: m.color }} />
              <span className="flex-1 text-left font-medium">{m.name}</span>
              <span
                className="text-[10px] px-1.5 py-0.5 rounded-md"
                style={{ background: m.color + "22", color: m.color }}
              >
                {m.tag}
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
