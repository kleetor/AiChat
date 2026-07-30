import { useState } from "react";
import { X, MessageSquarePlus } from "lucide-react";
import type { Prompt } from "@/lib/services";
import PromptSelector from "@/components/shared/PromptSelector";

interface NewConversationDialogProps {
  open: boolean;
  onClose: () => void;
  prompts: Prompt[];
  onCreate: (title: string, promptId: number | null) => void;
}

export default function NewConversationDialog({
  open,
  onClose,
  prompts,
  onCreate,
}: NewConversationDialogProps) {
  const [title, setTitle] = useState("");
  const [selectedPromptId, setSelectedPromptId] = useState<number | null>(null);

  if (!open) return null;

  const handleCreate = () => {
    onCreate(title.trim() || "", selectedPromptId);
    setTitle("");
    setSelectedPromptId(null);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="fixed inset-0 bg-black/40" onClick={onClose} />
      <div className="relative bg-background rounded-2xl shadow-2xl w-full max-w-[400px] mx-4 border border-border">
        <div className="flex items-center justify-between px-5 py-4 border-b border-border">
          <h2 className="text-base font-semibold flex items-center gap-2">
            <MessageSquarePlus size={16} />
            新建对话
          </h2>
          <button
            onClick={onClose}
            className="p-1 rounded-md text-muted-foreground hover:bg-accent hover:text-foreground"
          >
            <X size={16} />
          </button>
        </div>

        <div className="p-5 space-y-4">
          {/* 会话名称 */}
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-muted-foreground">会话名称</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="新对话"
              className="w-full text-xs px-3 py-2 rounded-lg border bg-transparent outline-none"
              style={{ borderColor: "hsl(var(--border))" }}
            />
          </div>

          {/* 提示词选择 */}
          <div className="space-y-1.5">
            <label className="text-xs font-medium text-muted-foreground">选择提示词</label>
            <PromptSelector
              prompts={prompts}
              value={selectedPromptId}
              onChange={setSelectedPromptId}
            />
          </div>

          {/* 按钮 */}
          <div className="flex gap-2 pt-2">
            <button
              onClick={onClose}
              className="flex-1 py-2 rounded-lg border text-xs font-medium text-muted-foreground hover:bg-accent"
              style={{ borderColor: "hsl(var(--border))" }}
            >
              取消
            </button>
            <button
              onClick={handleCreate}
              className="flex-1 py-2 rounded-lg bg-primary text-primary-foreground text-xs font-medium hover:opacity-90"
            >
              创建
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
