import { useState, useEffect, useMemo } from "react";
import { X, Plus, Edit2, Trash2, Search, RefreshCw, Brain } from "lucide-react";
import {
  getMemoryList, getMemoryEnabled, addMemory, updateMemory, toggleMemory, deleteMemory, clearMemories, searchMemories,
  type MemoryItem, type Prompt,
} from "@/lib/services";

interface MemoryModalProps {
  open: boolean;
  onClose: () => void;
  prompts: Prompt[];
}

function formatDate(s: string) {
  try { return new Date(s).toLocaleDateString("zh-CN", { month: "short", day: "numeric" }); } catch { return s; }
}

export default function MemoryModal({ open, onClose, prompts }: MemoryModalProps) {
  const [memories, setMemories] = useState<MemoryItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [tab, setTab] = useState<"all" | "enabled" | "search">("all");
  const [filterPromptId, setFilterPromptId] = useState<number | "all">("all");

  // Search
  const [searchQuery, setSearchQuery] = useState("");

  // Add bar
  const [addValue, setAddValue] = useState("");

  // Edit modal
  const [editOpen, setEditOpen] = useState(false);
  const [editId, setEditId] = useState<number | null>(null);
  const [editValue, setEditValue] = useState("");

  useEffect(() => {
    if (open) load();
  }, [open, tab]);

  const load = async () => {
    setLoading(true);
    try {
      if (tab === "enabled") {
        setMemories(await getMemoryEnabled());
      } else {
        setMemories(await getMemoryList());
      }
    } catch { /* ignore */ }
    setLoading(false);
  };

  const handleSearch = async () => {
    if (!searchQuery.trim()) { setTab("all"); load(); return; }
    setLoading(true);
    try { setMemories(await searchMemories(searchQuery.trim())); } catch { /* ignore */ }
    setLoading(false);
  };

  const handleAdd = async () => {
    if (!addValue.trim()) return;
    try { await addMemory(addValue.trim()); setAddValue(""); load(); } catch { /* ignore */ }
  };

  const handleToggle = async (id: number, enabled: boolean) => {
    try { await toggleMemory(id, enabled); load(); } catch { /* ignore */ }
  };

  const openEdit = (m: MemoryItem) => { setEditId(m.id); setEditValue(m.value); setEditOpen(true); };

  const saveEdit = async () => {
    if (!editValue.trim() || !editId) return;
    try { await updateMemory(editId, editValue.trim()); setEditOpen(false); load(); } catch { /* ignore */ }
  };

  const del = async (id: number) => {
    if (!confirm("确定删除此记忆？")) return;
    try { await deleteMemory(id); load(); } catch { /* ignore */ }
  };

  const clearAll = async () => {
    if (!confirm("确定清空所有记忆？此操作不可恢复。")) return;
    try { await clearMemories(); load(); } catch { /* ignore */ }
  };

  const detailLabel = (d: string) => {
    const m: Record<string, { c: string; t: string }> = {
      FULL: { c: "bg-emerald-100 text-emerald-700", t: "清晰" },
      BRIEF: { c: "bg-amber-100 text-amber-700", t: "模糊" },
      TITLE: { c: "bg-red-100 text-red-700", t: "轮廓" },
    };
    const x = m[d] || { c: "bg-muted text-muted-foreground", t: d };
    return <span className={`text-[10px] px-1.5 py-0.5 rounded-md font-medium ${x.c}`}>{x.t}</span>;
  };

  // 提示词名称映射
  const getPromptName = (promptId?: number): string | null => {
    if (promptId == null) return null;
    return prompts.find(p => p.id === promptId)?.name ?? null;
  };

  // 筛选后的记忆列表
  const filtered = useMemo(() => {
    if (filterPromptId === "all") return memories;
    return memories.filter(m => m.promptId === filterPromptId);
  }, [memories, filterPromptId]);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="fixed inset-0 bg-black/35" onClick={onClose} />
      <div className="relative bg-background rounded-2xl shadow-2xl w-full max-w-[640px] max-h-[80vh] mx-4 border border-border flex flex-col overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-border shrink-0">
          <h2 className="text-base font-semibold flex items-center gap-2"><Brain size={18} /> 长期记忆</h2>
          <button onClick={onClose} className="p-1 rounded-md text-muted-foreground hover:bg-accent hover:text-foreground"><X size={16} /></button>
        </div>

        {/* Add bar */}
        <div className="flex gap-2 px-5 py-3 border-b border-border shrink-0">
          <input
            value={addValue} onChange={e => setAddValue(e.target.value)}
            onKeyDown={e => e.key === "Enter" && handleAdd()}
            placeholder="手动添加一条记忆..."
            className="flex-1 text-xs px-3 py-2 rounded-lg border bg-transparent outline-none"
            style={{ borderColor: "hsl(var(--border))" }}
          />
          <button onClick={handleAdd} disabled={!addValue.trim()}
            className="px-3 py-2 rounded-lg bg-primary text-primary-foreground text-xs font-medium shrink-0 hover:opacity-90 disabled:opacity-50 flex items-center gap-1">
            <Plus size={13} />添加
          </button>
        </div>

        {/* Tabs + Filter */}
        <div className="flex items-center gap-2 px-5 py-2 border-b border-border shrink-0 flex-wrap">
          <div className="flex gap-1 bg-muted rounded-lg p-0.5">
            {(["all", "enabled", "search"] as const).map(t => (
              <button key={t} onClick={() => setTab(t)}
                className={`px-3 py-1.5 rounded-md text-[11px] font-medium transition-colors ${tab === t ? "bg-background text-foreground shadow-sm" : "text-muted-foreground"}`}>
                {t === "all" ? "全部" : t === "enabled" ? "已启用" : "搜索"}
              </button>
            ))}
          </div>
          <select
            value={filterPromptId}
            onChange={e => setFilterPromptId(e.target.value === "all" ? "all" : Number(e.target.value))}
            className="text-[11px] px-2 py-1.5 rounded-md border bg-transparent outline-none"
            style={{ borderColor: "hsl(var(--border))" }}
          >
            <option value="all">所有提示词</option>
            {prompts.map(p => (
              <option key={p.id} value={p.id}>{p.name}</option>
            ))}
          </select>
          {tab === "search" && (
            <div className="flex gap-1.5 flex-1">
              <input value={searchQuery} onChange={e => setSearchQuery(e.target.value)}
                onKeyDown={e => e.key === "Enter" && handleSearch()}
                placeholder="搜索记忆内容..." className="flex-1 text-[11px] px-2 py-1.5 rounded-md border bg-transparent outline-none" style={{ borderColor: "hsl(var(--border))" }} />
              <button onClick={handleSearch} className="px-2 py-1.5 rounded-md text-[11px] bg-primary text-primary-foreground"><Search size={12} /></button>
            </div>
          )}
        </div>

        {/* List */}
        <div className="flex-1 overflow-y-auto p-4 space-y-2">
          {loading && <p className="text-xs text-muted-foreground text-center py-8">加载中...</p>}
          {!loading && filtered.length === 0 && (
            <p className="text-xs text-muted-foreground text-center py-8">
              {tab === "search" ? "没有找到匹配的记忆" : "暂无记忆，AI会在对话中自动提取"}
            </p>
          )}
          {filtered.map(m => (
            <div key={m.id} className={`p-3 rounded-xl border border-border ${m.enabled ? "" : "opacity-45"}`}>
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0 flex-1">
                  <p className="text-xs leading-relaxed break-words">{m.value}</p>
                  <p className="text-[10px] text-muted-foreground mt-1.5 flex items-center gap-2 flex-wrap">
                    {getPromptName(m.promptId) && (
                      <span className="bg-violet-100 text-violet-700 text-[10px] px-1.5 py-0.5 rounded-md font-medium">
                        {getPromptName(m.promptId)}
                      </span>
                    )}
                    {detailLabel(m.detailLevel)}
                    <span className={`text-[10px] px-1.5 py-0.5 rounded-md font-medium ${m.source === "MANUAL" ? "bg-blue-100 text-blue-700" : "bg-muted text-muted-foreground"}`}>{m.source === "MANUAL" ? "手动" : "自动"}</span>
                    <span>{m.accessCount}次访问</span>
                    <span>{formatDate(m.lastAccessedAt)}</span>
                  </p>
                </div>
                <div className="flex gap-1 shrink-0">
                  <button onClick={() => handleToggle(m.id, !m.enabled)} className="text-[10px] px-2 py-1 rounded hover:bg-accent text-muted-foreground"
                    title={m.enabled ? "禁用" : "启用"}>{m.enabled ? "禁用" : "启用"}</button>
                  <button onClick={() => openEdit(m)} className="p-1 rounded hover:bg-accent text-muted-foreground" title="编辑"><Edit2 size={12} /></button>
                  <button onClick={() => del(m.id)} className="p-1 rounded hover:bg-destructive/10 hover:text-destructive text-muted-foreground" title="删除"><Trash2 size={12} /></button>
                </div>
              </div>
            </div>
          ))}
        </div>

        {/* Footer */}
        <div className="flex gap-2 px-5 py-3 border-t border-border shrink-0">
          <button onClick={clearAll} className="flex-1 py-2 rounded-lg text-[11px] text-muted-foreground hover:text-destructive hover:bg-destructive/10 border border-border flex items-center justify-center gap-1">
            <Trash2 size={12} />清空全部
          </button>
          <button onClick={() => { load(); }} className="flex-1 py-2 rounded-lg text-[11px] text-muted-foreground hover:bg-accent border border-border flex items-center justify-center gap-1">
            <RefreshCw size={12} />刷新
          </button>
        </div>

        {/* Edit modal */}
        {editOpen && (
          <div className="absolute inset-0 z-10 flex items-center justify-center bg-black/20 rounded-2xl">
            <div className="bg-background rounded-xl p-5 w-[360px] border shadow-xl">
              <h3 className="text-sm font-medium mb-3">编辑记忆</h3>
              <textarea value={editValue} onChange={e => setEditValue(e.target.value)} rows={4}
                className="w-full text-xs px-3 py-2 rounded-lg border bg-transparent outline-none resize-none"
                style={{ borderColor: "hsl(var(--border))" }} />
              <div className="flex gap-2 mt-3">
                <button onClick={saveEdit} className="flex-1 py-2 rounded-lg bg-primary text-primary-foreground text-xs font-medium">保存</button>
                <button onClick={() => setEditOpen(false)} className="flex-1 py-2 rounded-lg border text-xs text-muted-foreground" style={{ borderColor: "hsl(var(--border))" }}>取消</button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
