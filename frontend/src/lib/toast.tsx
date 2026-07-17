import { createContext, useContext, useState, useCallback, type ReactNode } from "react";
import { X } from "lucide-react";

type ToastType = "success" | "error" | "info";

interface Toast {
  id: number;
  type: ToastType;
  message: string;
}

interface ToastContextType {
  toast: (type: ToastType, message: string) => void;
}

const ToastContext = createContext<ToastContextType | null>(null);

let toastId = 0;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const toast = useCallback((type: ToastType, message: string) => {
    const id = ++toastId;
    setToasts(prev => [...prev.slice(-4), { id, type, message }]);
    setTimeout(() => {
      setToasts(prev => prev.filter(t => t.id !== id));
    }, 3500);
  }, []);

  const remove = (id: number) => {
    setToasts(prev => prev.filter(t => t.id !== id));
  };

  const typeStyles: Record<ToastType, string> = {
    success: "border-green-500/40 text-green-700 dark:text-green-400",
    error: "border-destructive/50 text-destructive",
    info: "border-primary/40 text-primary",
  };

  const typeBgs: Record<ToastType, string> = {
    success: "bg-green-50 dark:bg-green-950/40",
    error: "bg-red-50 dark:bg-red-950/40",
    info: "bg-primary/5 dark:bg-primary/10",
  };

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      {/* Toast container */}
      <div className="fixed bottom-4 right-4 z-[100] flex flex-col gap-2 pointer-events-none">
        {toasts.map(t => (
          <div
            key={t.id}
            className={`pointer-events-auto flex items-center gap-2 px-3 py-2.5 rounded-lg border shadow-lg text-xs max-w-xs animate-in ${typeStyles[t.type]} ${typeBgs[t.type]}`}
          >
            <span className="flex-1">{t.message}</span>
            <button
              onClick={() => remove(t.id)}
              className="shrink-0 p-0.5 rounded opacity-60 hover:opacity-100"
            >
              <X size={12} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextType {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast must be used within ToastProvider");
  return ctx;
}
