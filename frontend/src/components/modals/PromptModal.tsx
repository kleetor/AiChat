import { useState, useEffect } from "react";
import { X, FileText, Plus, Sparkles, Pencil, Trash2 } from "lucide-react";
import type { Prompt } from "@/lib/services";

interface PromptModalProps {
  open: boolean;
  onClose: () => void;
  prompts: Prompt[];
  onSelectPrompt: (prompt: Prompt) => void;
  onDeletePrompt: (id: number) => Promise<void>;
  onNewPrompt: () => void;
  onHubOpen: () => void;
  onSavePrompt: (name: string, content: string) => Promise<void>;
  onUpdatePrompt: (id: number, name: string, content: string) => Promise<void>;
}

export default function PromptModal({
  open,
  onClose,
  prompts,
  onSelectPrompt,
  onDeletePrompt,
  onNewPrompt,
  onHubOpen,
  onSavePrompt,
  onUpdatePrompt,
}: PromptModalProps) {
  const [editing, setEditing] = useState(false);
  const [editId, setEditId] = useState<number | null>(null);
  const [editName, setEditName] = useState("");
  const [editContent, setEditContent] = useState("");
  const [saving, setSaving] = useState(false);

  // Reset editing state when modal opens
  useEffect(() => {
    if (open) {
      setEditing(false);
      setEditId(null);
      setEditName("");
      setEditContent("");
    }
  }, [open]);

  if (!open) return null;

  const startCreate = () => {
    setEditId(null);
    setEditName("");
    setEditContent("");
    setEditing(true);
    onNewPrompt();
  };

  const startEdit = (p: Prompt) => {
    setEditId(p.id);
    setEditName(p.name);
    setEditContent(p.content);
    setEditing(true);
  };

  const handleSave = async () => {
    if (!editName.trim() || !editContent.trim()) return;
    setSaving(true);
    try {
      if (editId) {
        await onUpdatePrompt(editId, editName.trim(), editContent.trim());
      } else {
        await onSavePrompt(editName.trim(), editContent.trim());
      }
      setEditing(false);
      setEditId(null);
      setEditName("");
      setEditContent("");
    } catch {
      // error handled by parent
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="fixed inset-0 bg-black/40" onClick={onClose} />
      <div className="relative bg-background rounded-2xl shadow-2xl w-full max-w-[480px] max-h-[80vh] overflow-y-auto mx-4 border border-border">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-border">
          <h2 className="text-base font-semibold flex items-center gap-2">
            <FileText size={16} />
            {editing ? (editId ? "编辑提示词" : "新建提示词") : "自定义提示词"}
          </h2>
          <button onClick={onClose} className="p-1 rounded-md text-muted-foreground hover:bg-accent hover:text-foreground">
            <X size={16} />
          </button>
        </div>

        <div className="p-5">
          {editing ? (
            <div className="space-y-4">
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-muted-foreground">名称</label>
                <input
                  type="text" value={editName} onChange={(e) => setEditName(e.target.value)}
                  placeholder="输入名称"
                  className="w-full text-xs px-3 py-2 rounded-lg border bg-transparent outline-none"
                  style={{ borderColor: "hsl(var(--border))" }}
                />
              </div>
              <div className="space-y-1.5">
                <label className="text-xs font-medium text-muted-foreground">内容 (System Prompt)</label>
                <textarea
                  value={editContent} onChange={(e) => setEditContent(e.target.value)} rows={5}
                  placeholder="请输入系统提示词内容..."
                  className="w-full text-xs px-3 py-2 rounded-lg border bg-transparent outline-none resize-none"
                  style={{ borderColor: "hsl(var(--border))" }}
                />
              </div>
              <div className="flex gap-2">
                <button onClick={handleSave} disabled={saving}
                  className="flex-1 py-2 rounded-lg bg-primary text-primary-foreground text-xs font-medium hover:opacity-90 disabled:opacity-50">
                  {saving ? "保存中..." : "保存"}
                </button>
                <button onClick={() => setEditing(false)}
                  className="flex-1 py-2 rounded-lg border text-xs font-medium text-muted-foreground hover:bg-accent"
                  style={{ borderColor: "hsl(var(--border))" }}>
                  取消
                </button>
              </div>
            </div>
          ) : (
            <div className="space-y-3">
              <div className="space-y-1 max-h-[40vh] overflow-y-auto">
                {prompts.length === 0 ? (
                  <p className="text-[11px] text-muted-foreground/60 text-center py-6">暂无提示词</p>
                ) : (
                  prompts.map((p) => (
                    <div
                      key={p.id}
                      onClick={() => { onSelectPrompt(p); onClose(); }}
                      className="w-full text-left px-3 py-2.5 rounded-lg flex items-center justify-between gap-2 group hover:bg-accent transition-colors cursor-pointer"
                    >
                      <div className="min-w-0">
                        <p className="text-xs font-medium text-foreground truncate">{p.name}</p>
                        <p className="text-[11px] text-muted-foreground truncate">{p.content}</p>
                      </div>
                      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity shrink-0">
                        <button onClick={(e) => { e.stopPropagation(); startEdit(p); }}
                          className="p-1 rounded text-muted-foreground hover:text-foreground"><Pencil size={12} /></button>
                        <button onClick={async (e) => { e.stopPropagation(); await onDeletePrompt(p.id); }}
                          className="p-1 rounded text-muted-foreground hover:text-destructive"><Trash2 size={12} /></button>
                      </div>
                    </div>
                  ))
                )}
              </div>

              <div className="flex gap-2 pt-2">
                <button onClick={startCreate}
                  className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg border text-xs font-medium text-muted-foreground hover:bg-accent"
                  style={{ borderColor: "hsl(var(--border))" }}>
                  <Plus size={13} /> 新建提示词
                </button>
                <button onClick={onHubOpen}
                  className="flex-1 flex items-center justify-center gap-1.5 py-2 rounded-lg border text-xs font-medium text-muted-foreground hover:bg-accent"
                  style={{ borderColor: "hsl(var(--border))" }}>
                  <Sparkles size={13} /> 提示词社区
                </button>
              </div>

              <button onClick={onClose}
                className="w-full py-2 rounded-lg bg-primary text-primary-foreground text-xs font-medium hover:opacity-90">
                关闭
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
