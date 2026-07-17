import { useState, useEffect, useRef } from "react";
import { X, User, Lock, Camera, Upload, Check } from "lucide-react";

interface ProfileModalProps {
  open: boolean;
  onClose: () => void;
  username: string;
  email: string;
  pid: string;
  signature: string;
  avatarUrl: string;
  onSaveSignature: (sig: string) => Promise<void>;
  onChangePassword: (currentPassword: string, newPassword: string) => Promise<void>;
  onVerifyPassword?: (password: string) => Promise<boolean>;
  onAvatarUpload: (file: File) => Promise<string>;
}

export default function ProfileModal({
  open,
  onClose,
  username,
  email,
  pid,
  signature,
  avatarUrl,
  onSaveSignature,
  onChangePassword,
  onVerifyPassword,
  onAvatarUpload,
}: ProfileModalProps) {
  const [sigValue, setSigValue] = useState(signature);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);

  // 同步 prop 变更
  useEffect(() => { setSigValue(signature); }, [signature]);

  const [pwStep, setPwStep] = useState<"none" | "verify" | "new">("none");
  // 使用 ref 存储密码，避免明文暴露在 React DevTools
  const pwVerifyRef = useRef<HTMLInputElement>(null);
  const pwNewRef = useRef<HTMLInputElement>(null);
  const pwConfirmRef = useRef<HTMLInputElement>(null);
  // 独立保存已验证的密码字符串，避免 DOM unmount 后丢失
  const pwVerifyValueRef = useRef("");
  const [pwError, setPwError] = useState("");

  if (!open) return null;

  const handleSaveSig = async () => {
    setSaving(true);
    await onSaveSignature(sigValue);
    setSaving(false);
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };

  const handleAvatarClick = () => {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = "image/*";
    input.onchange = async (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (file) {
        await onAvatarUpload(file);
      }
    };
    input.click();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="fixed inset-0 bg-black/40" onClick={onClose} />
      <div className="relative bg-background rounded-2xl shadow-2xl w-full max-w-[500px] max-h-[85vh] overflow-y-auto mx-4 border border-border">
        <div className="flex items-center justify-between px-5 py-4 border-b border-border">
          <h2 className="text-base font-semibold flex items-center gap-2">
            <User size={16} /> 个人信息
          </h2>
          <button onClick={onClose} className="p-1 rounded-md text-muted-foreground hover:bg-accent hover:text-foreground"><X size={16} /></button>
        </div>

        <div className="p-5">
          <div className="space-y-5">
            {/* Avatar */}
            <div className="flex flex-col items-center gap-3">
              <div className="relative group cursor-pointer" onClick={handleAvatarClick}>
                {avatarUrl ? (
                  <img src={avatarUrl} alt="头像" className="w-20 h-20 rounded-full object-cover" />
                ) : (
                  <div className="w-20 h-20 rounded-full bg-accent flex items-center justify-center text-foreground">
                    <Camera size={28} />
                  </div>
                )}
                <div className="absolute inset-0 rounded-full bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                  <Upload size={18} color="#fff" />
                </div>
              </div>
              <span className="text-[11px] text-muted-foreground">点击更换头像</span>
            </div>

            {[
              { label: "用户名", value: username },
              { label: "邮箱", value: email },
              { label: "用户PID", value: pid },
            ].map((row) => (
              <div key={row.label} className="flex justify-between items-center py-2 border-b border-border/50">
                <span className="text-xs text-muted-foreground">{row.label}</span>
                <span className="text-xs text-foreground">{row.value}</span>
              </div>
            ))}

            <div className="space-y-2">
              <span className="text-xs text-muted-foreground">个性签名</span>
              <div className="flex gap-2">
                <input
                  type="text" value={sigValue} onChange={(e) => { setSigValue(e.target.value); setSaved(false); }}
                  placeholder="写下你的个性签名..." maxLength={200}
                  className="flex-1 text-xs px-3 py-2 rounded-lg border bg-transparent outline-none"
                  style={{ borderColor: "hsl(var(--border))" }}
                />
                <button onClick={handleSaveSig} disabled={saving}
                  className="shrink-0 px-3 py-2 rounded-lg bg-primary text-primary-foreground text-xs font-medium hover:opacity-90 disabled:opacity-50">
                  {saving ? "..." : "保存"}
                </button>
              </div>
              {saved && <span className="text-[11px] text-green-600 flex items-center gap-1"><Check size={11} /> 已保存</span>}
            </div>

            {/* Password change */}
            {pwStep === "none" && (
              <button onClick={() => setPwStep("verify")}
                className="w-full flex items-center justify-center gap-2 py-2.5 rounded-lg text-xs font-medium border text-muted-foreground hover:bg-accent"
                style={{ borderColor: "hsl(var(--border))" }}>
                <Lock size={13} /> 修改密码
              </button>
            )}
            {pwStep === "verify" && (
              <div className="space-y-3 p-3 border rounded-lg" style={{ borderColor: "hsl(var(--border))" }}>
                <p className="text-xs font-medium">验证原密码</p>
                <input type="password" ref={pwVerifyRef} placeholder="请输入当前密码"
                  className="w-full text-xs px-3 py-2 rounded-lg border bg-transparent outline-none"
                  style={{ borderColor: "hsl(var(--border))" }} />
                {pwError && <p className="text-[11px] text-destructive">{pwError}</p>}
                <div className="flex gap-2">
                  <button onClick={async () => {
                    if (!onVerifyPassword) return;
                    const verifyVal = pwVerifyRef.current?.value || "";
                    const ok = await onVerifyPassword(verifyVal);
                    if (ok) { pwVerifyValueRef.current = verifyVal; setPwStep("new"); setPwError(""); }
                    else setPwError("密码错误");
                  }} className="flex-1 py-2 rounded-lg bg-primary text-primary-foreground text-xs font-medium">验证</button>
                  <button onClick={() => { setPwStep("none"); if (pwVerifyRef.current) pwVerifyRef.current.value = ""; setPwError(""); }}
                    className="flex-1 py-2 rounded-lg border text-xs text-muted-foreground" style={{ borderColor: "hsl(var(--border))" }}>取消</button>
                </div>
              </div>
            )}
            {pwStep === "new" && (
              <div className="space-y-3 p-3 border rounded-lg" style={{ borderColor: "hsl(var(--border))" }}>
                <p className="text-xs font-medium">设置新密码</p>
                <input type="password" ref={pwNewRef} placeholder="新密码（至少8位）"
                  className="w-full text-xs px-3 py-2 rounded-lg border bg-transparent outline-none"
                  style={{ borderColor: "hsl(var(--border))" }} />
                <input type="password" ref={pwConfirmRef} placeholder="确认新密码"
                  className="w-full text-xs px-3 py-2 rounded-lg border bg-transparent outline-none"
                  style={{ borderColor: "hsl(var(--border))" }} />
                {pwError && <p className="text-[11px] text-destructive">{pwError}</p>}
                <div className="flex gap-2">
                  <button onClick={async () => {
                    const newVal = pwNewRef.current?.value || "";
                    const confirmVal = pwConfirmRef.current?.value || "";
                    if (newVal.length < 6) { setPwError("密码至少6位"); return; }
                    if (newVal !== confirmVal) { setPwError("两次密码不一致"); return; }
                    try {
                      await onChangePassword(pwVerifyValueRef.current, newVal);
                      setPwStep("none");
                      pwVerifyValueRef.current = "";
                      if (pwNewRef.current) pwNewRef.current.value = "";
                      if (pwConfirmRef.current) pwConfirmRef.current.value = "";
                      setPwError("");
                    } catch (e: unknown) {
                      setPwError(e instanceof Error ? e.message : "修改失败");
                    }
                  }} className="flex-1 py-2 rounded-lg bg-primary text-primary-foreground text-xs font-medium">确认修改</button>
                  <button onClick={() => {
                    setPwStep("none");
                    if (pwNewRef.current) pwNewRef.current.value = "";
                    if (pwConfirmRef.current) pwConfirmRef.current.value = "";
                    setPwError("");
                  }}
                    className="flex-1 py-2 rounded-lg border text-xs text-muted-foreground" style={{ borderColor: "hsl(var(--border))" }}>取消</button>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
