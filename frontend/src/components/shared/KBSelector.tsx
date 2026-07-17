import { useState, useRef, useEffect } from "react";
import { ChevronDown, Library } from "lucide-react";

interface KBSelectorProps {
  value: string;
  options: { value: string; label: string }[];
  onChange: (value: string) => void;
}

export default function KBSelector({ value, options, onChange }: KBSelectorProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const current = options.find((o) => o.value === value);
  const label = current && current.value !== "" ? current.label : "知识库";

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
        className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-medium transition-all duration-150 ${
          current && current.value !== ""
            ? "bg-accent text-foreground"
            : "text-muted-foreground hover:bg-accent hover:text-foreground"
        }`}
      >
        <Library size={13} />
        <span className="max-w-[80px] truncate">{label}</span>
        <ChevronDown size={12} className="text-muted-foreground" />
      </button>

      {open && (
        <div className="absolute top-full left-0 mt-1.5 rounded-xl overflow-hidden z-50 w-44 py-1 bg-popover border shadow-[0_8px_24px_rgba(0,0,0,0.1)]">
          {options.map((opt) => (
            <button
              key={opt.value}
              onClick={() => {
                onChange(opt.value);
                setOpen(false);
              }}
              className={`w-full text-left px-3 py-2 text-xs transition-all duration-100 truncate ${
                value === opt.value ? "bg-accent text-foreground" : "text-muted-foreground hover:bg-accent"
              }`}
            >
              {opt.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
