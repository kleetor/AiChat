import { useState, useEffect, useRef } from "react";
import { X, Upload, Trash2, RefreshCw, Plus, Edit2, ArrowLeft } from "lucide-react";
import {
  getKBList, createKB, updateKB, deleteKB,
  getKBDocuments, uploadKBDocument, deleteKBDocument, reindexKBDocument,
  type KnowledgeBase, type KbDocument,
} from "@/lib/services";

interface KBModalProps {
  open: boolean;
  onClose: () => void;
}

export default function KBModal({ open, onClose }: KBModalProps) {
  const [kbs, setKbs] = useState<KnowledgeBase[]>([]);
  const [loading, setLoading] = useState(false);

  // Edit/Create modal
  const [editOpen, setEditOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editName, setEditName] = useState("");
  const [editDesc, setEditDesc] = useState("");
  const [editPrompt, setEditPrompt] = useState("");
  const [editChunkSize, setEditChunkSize] = useState("");
  const [editChunkOverlap, setEditChunkOverlap] = useState("");
  const [editErr, setEditErr] = useState("");

  // Docs view
  const [currentKb, setCurrentKb] = useState<KnowledgeBase | null>(null);
  const [docs, setDocs] = useState<KbDocument[]>([]);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (open) loadKBs();
    return () => { if (pollRef.current) clearInterval(pollRef.current); };
  }, [open]);

  // ---- KB List ----
  const loadKBs = async () => {
    setLoading(true);
    try { setKbs(await getKBList()); } catch { /* ignore */ }
    setLoading(false);
  };

  // ---- KB CRUD ----
  const openCreate = () => { setEditingId(null); setEditName(""); setEditDesc(""); setEditPrompt(""); setEditChunkSize(""); setEditChunkOverlap(""); setEditErr(""); setEditOpen(true); };
  const openEdit = (kb: KnowledgeBase) => { setEditingId(kb.id); setEditName(kb.name); setEditDesc(kb.description || ""); setEditPrompt(kb.promptTemplate || ""); setEditChunkSize(kb.chunkSize?.toString() || ""); setEditChunkOverlap(kb.chunkOverlap?.toString() || ""); setEditErr(""); setEditOpen(true); };

  const saveKB = async () => {
    if (!editName.trim()) { setEditErr("请输入名称"); return; }
    try {
      const pt = editPrompt.trim() || undefined;
      const cs = editChunkSize.trim() ? Number(editChunkSize.trim()) : null;
      const co = editChunkOverlap.trim() ? Number(editChunkOverlap.trim()) : null;
      if (editingId) await updateKB(editingId, editName.trim(), editDesc.trim(), pt, cs, co);
      else await createKB(editName.trim(), editDesc.trim(), pt, cs, co);
      setEditOpen(false);
      loadKBs();
    } catch { setEditErr("保存失败"); }
  };

  const delKB = async (id: number) => {
    if (!confirm("确定删除此知识库？所有文档将被永久删除。")) return;
    try { await deleteKB(id); loadKBs(); } catch { /* ignore */ }
  };

  // ---- Docs ----
  const viewDocs = async (kb: KnowledgeBase) => {
    setCurrentKb(kb);
    await loadDocs(kb.id);
    pollRef.current = setInterval(() => loadDocs(kb.id), 4000);
  };

  const loadDocs = async (kbId: number) => {
    try {
      const list = await getKBDocuments(kbId);
      setDocs(list);
      if (list.every(d => d.status !== "PROCESSING") && pollRef.current) {
        clearInterval(pollRef.current);
        pollRef.current = null;
      }
    } catch { /* ignore */ }
  };

  const goBack = () => { setCurrentKb(null); setDocs([]); if (pollRef.current) { clearInterval(pollRef.current); pollRef.current = null; } };

  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !currentKb) return;
    try { await uploadKBDocument(currentKb.id, file); loadDocs(currentKb.id); } catch { /* ignore */ }
    e.target.value = "";
  };

  const delDoc = async (id: number) => {
    if (!confirm("确定删除此文档？")) return;
    try { await deleteKBDocument(id); if (currentKb) loadDocs(currentKb.id); loadKBs(); } catch { /* ignore */ }
  };

  const reindex = async (id: number) => {
    try { await reindexKBDocument(id); if (currentKb) loadDocs(currentKb.id); } catch { /* ignore */ }
  };

  const statusBadge = (s: string) => {
    const m: Record<string, string> = { READY: "bg-emerald-100 text-emerald-700", PROCESSING: "bg-amber-100 text-amber-700", ERROR: "bg-red-100 text-red-700" };
    return <span className={`text-[10px] px-1.5 py-0.5 rounded-md font-medium ${m[s] || "bg-muted text-muted-foreground"}`}>{s}</span>;
  };

  const fmtSize = (b: number) => b < 1024 ? `${b} B` : b < 1048576 ? `${(b / 1024).toFixed(1)} KB` : `${(b / 1048576).toFixed(1)} MB`;

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="fixed inset-0 bg-black/35" onClick={onClose} />
      <div className="relative bg-background rounded-2xl shadow-2xl w-full max-w-[600px] max-h-[80vh] mx-4 border border-border flex flex-col overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-border shrink-0">
          <div className="flex items-center gap-2">
            {currentKb && (
              <button onClick={goBack} className="p-1 rounded-md text-muted-foreground hover:text-foreground"><ArrowLeft size={15} /></button>
            )}
            <h2 className="text-base font-semibold">{currentKb ? `📁 ${currentKb.name}` : "知识库管理"}</h2>
          </div>
          <button onClick={onClose} className="p-1 rounded-md text-muted-foreground hover:bg-accent hover:text-foreground"><X size={16} /></button>
        </div>

        {/* KB List */}
        {!currentKb && (
          <>
            <div className="flex-1 overflow-y-auto p-4 space-y-2">
              {loading && <p className="text-xs text-muted-foreground text-center py-8">加载中...</p>}
              {!loading && kbs.length === 0 && <p className="text-xs text-muted-foreground text-center py-8">暂无知识库</p>}
              {kbs.map(kb => (
                <div key={kb.id} className="flex items-center justify-between p-3 rounded-xl border border-border hover:bg-accent/50 cursor-pointer transition-colors"
                  onClick={() => viewDocs(kb)}>
                  <div>
                    <p className="text-xs font-medium">{kb.name}</p>
                    <p className="text-[10px] text-muted-foreground mt-0.5">{kb.docCount || 0} 文档 · {kb.chunkCount || 0} 分块</p>
                  </div>
                  <div className="flex gap-1.5" onClick={e => e.stopPropagation()}>
                    <button onClick={() => openEdit(kb)} className="p-1.5 rounded-md text-muted-foreground hover:bg-accent" title="编辑"><Edit2 size={13} /></button>
                    <button onClick={() => delKB(kb.id)} className="p-1.5 rounded-md text-muted-foreground hover:text-destructive hover:bg-destructive/10" title="删除"><Trash2 size={13} /></button>
                  </div>
                </div>
              ))}
            </div>
            <div className="p-4 border-t border-border shrink-0">
              <button onClick={openCreate} className="w-full flex items-center justify-center gap-2 py-2 rounded-lg bg-primary text-primary-foreground text-xs font-medium">
                <Plus size={14} />新建知识库
              </button>
            </div>
          </>
        )}

        {/* Docs view */}
        {currentKb && (
          <>
            <div className="flex-1 overflow-y-auto p-4 space-y-2">
              {docs.length === 0 && <p className="text-xs text-muted-foreground text-center py-8">暂无文档</p>}
              {docs.map(d => (
                <div key={d.id} className="flex items-center justify-between p-3 rounded-xl border border-border">
                  <div className="min-w-0 flex-1">
                    <p className="text-xs font-medium truncate">{d.fileName}</p>
                    <p className="text-[10px] text-muted-foreground mt-0.5 flex items-center gap-2">
                      {fmtSize(d.fileSize)} · {d.chunkCount} 分块 · {statusBadge(d.status)}
                      {d.errorMsg && <span className="text-red-500 truncate">{d.errorMsg}</span>}
                    </p>
                  </div>
                  <div className="flex gap-1.5 shrink-0">
                    <button onClick={() => reindex(d.id)} disabled={d.status === "PROCESSING"} className="p-1.5 rounded-md text-muted-foreground hover:bg-accent disabled:opacity-30" title="重新索引"><RefreshCw size={13} /></button>
                    <button onClick={() => delDoc(d.id)} className="p-1.5 rounded-md text-muted-foreground hover:text-destructive hover:bg-destructive/10" title="删除"><Trash2 size={13} /></button>
                  </div>
                </div>
              ))}
            </div>
            <div className="p-4 border-t border-border shrink-0">
              <label className="w-full flex items-center justify-center gap-2 py-2 rounded-lg border border-dashed border-border text-xs text-muted-foreground cursor-pointer hover:border-primary/50 hover:text-primary transition-colors">
                <Upload size={14} />上传文档 (TXT / MD / PDF / DOCX)
                <input type="file"
                  accept=".txt,.md,.pdf,.docx"
                  className="hidden" onChange={handleFileSelect} />
              </label>
            </div>
          </>
        )}

        {/* Edit KB Modal — 独立于 overflow-hidden 主容器，避免裁剪 */}
        {editOpen && (
          <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/20" onClick={() => setEditOpen(false)}>
            <div className="bg-background rounded-xl p-5 w-[360px] border shadow-xl max-h-[85vh] flex flex-col" onClick={e => e.stopPropagation()}>
              <h3 className="text-sm font-medium mb-3 shrink-0">{editingId ? "编辑知识库" : "新建知识库"}</h3>
              <div className="flex-1 overflow-y-auto space-y-2 pr-0.5">
                <input value={editName} onChange={e => setEditName(e.target.value)} placeholder="名称" className="w-full text-xs px-3 py-2 rounded-lg border bg-transparent outline-none" style={{ borderColor: "hsl(var(--border))" }} />
                <input value={editDesc} onChange={e => setEditDesc(e.target.value)} placeholder="描述（可选）" className="w-full text-xs px-3 py-2 rounded-lg border bg-transparent outline-none" style={{ borderColor: "hsl(var(--border))" }} />
                <textarea
                  value={editPrompt}
                  onChange={e => setEditPrompt(e.target.value)}
                  placeholder="回答风格（可选，支持 {context} / {query} 占位符）"
                  rows={4}
                  className="w-full text-xs px-3 py-2 rounded-lg border bg-transparent outline-none resize-none"
                  style={{ borderColor: "hsl(var(--border))" }}
                />
                <p className="text-[10px] text-muted-foreground leading-relaxed">
                  占位符：<code className="bg-muted px-1 rounded">{`{context}`}</code> 检索到的文档内容，<code className="bg-muted px-1 rounded">{`{query}`}</code> 用户问题。<br />
                  留空则使用默认模板。提示：客服回复请设置字数限制。
                </p>
                <div className="flex gap-2">
                  <div className="flex-1">
                    <label className="text-[10px] text-muted-foreground block mb-1">分块大小（字符）</label>
                    <input
                      type="number" min="100" max="2000" step="50"
                      value={editChunkSize} onChange={e => setEditChunkSize(e.target.value)}
                      placeholder="默认 500"
                      className="w-full text-xs px-3 py-2 rounded-lg border bg-transparent outline-none"
                      style={{ borderColor: "hsl(var(--border))" }}
                    />
                  </div>
                  <div className="flex-1">
                    <label className="text-[10px] text-muted-foreground block mb-1">重叠字符数</label>
                    <input
                      type="number" min="0" max="500" step="10"
                      value={editChunkOverlap} onChange={e => setEditChunkOverlap(e.target.value)}
                      placeholder="默认 50"
                      className="w-full text-xs px-3 py-2 rounded-lg border bg-transparent outline-none"
                      style={{ borderColor: "hsl(var(--border))" }}
                    />
                  </div>
                </div>
                {editErr && <p className="text-[11px] text-destructive">{editErr}</p>}
              </div>
              <div className="flex gap-2 shrink-0 mt-3">
                <button onClick={saveKB} className="flex-1 py-2 rounded-lg bg-primary text-primary-foreground text-xs font-medium">保存</button>
                <button onClick={() => setEditOpen(false)} className="flex-1 py-2 rounded-lg border text-xs text-muted-foreground" style={{ borderColor: "hsl(var(--border))" }}>取消</button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
