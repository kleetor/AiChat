import { useState } from "react";
import { X, Wallet, Heart, Upload, Camera, Coins, CalendarCheck, CheckCircle, Loader2 } from "lucide-react";
import type { TokenUsage } from "@/lib/services";

interface WalletModalProps {
  open: boolean;
  onClose: () => void;
  balance: string;
  usageRecords: TokenUsage[];
  onSponsor: (file: File, amount: number) => Promise<void>;
  checkedIn: boolean;
  onCheckin: () => Promise<void>;
}

export default function WalletModal({
  open,
  onClose,
  balance,
  usageRecords,
  onSponsor,
  checkedIn,
  onCheckin,
}: WalletModalProps) {
  const [showSponsor, setShowSponsor] = useState(false);
  const [sponsorFile, setSponsorFile] = useState<File | null>(null);
  const [sponsorPreview, setSponsorPreview] = useState("");
  const [sponsorAmount, setSponsorAmount] = useState("");
  const [sponsorError, setSponsorError] = useState("");
  const [sponsorSubmitting, setSponsorSubmitting] = useState(false);
  const [checkinLoading, setCheckinLoading] = useState(false);

  if (!open) return null;

  const handleSponsorFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      setSponsorFile(file);
      const reader = new FileReader();
      reader.onload = () => setSponsorPreview(reader.result as string);
      reader.readAsDataURL(file);
    }
  };

  const handleSponsorSubmit = async () => {
    if (!sponsorFile) { setSponsorError("请选择截图"); return; }
    const amt = parseFloat(sponsorAmount);
    if (!amt || amt <= 0) { setSponsorError("请输入有效金额"); return; }
    setSponsorSubmitting(true);
    try {
      await onSponsor(sponsorFile, amt);
      resetSponsorForm();
      onClose();
    } catch (e: unknown) {
      setSponsorError(e instanceof Error ? e.message : "提交失败");
    } finally {
      setSponsorSubmitting(false);
    }
  };

  const resetSponsorForm = () => {
    setShowSponsor(false);
    setSponsorFile(null);
    setSponsorPreview("");
    setSponsorAmount("");
    setSponsorError("");
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="fixed inset-0 bg-black/40" onClick={onClose} />
      <div className="relative bg-background rounded-2xl shadow-2xl w-full max-w-[560px] max-h-[85vh] overflow-y-auto mx-4 border border-border">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-border">
          <h2 className="text-base font-semibold flex items-center gap-2">
            <Wallet size={16} /> 我的钱包
          </h2>
          <button onClick={onClose} className="p-1 rounded-md text-muted-foreground hover:bg-accent hover:text-foreground"><X size={16} /></button>
        </div>

        {showSponsor ? (
          /* Sponsor panel */
          <div className="p-5 space-y-4">
            <button onClick={() => resetSponsorForm()}
              className="text-xs text-muted-foreground hover:text-foreground flex items-center gap-1">
              &larr; 返回钱包
            </button>

            <p className="text-[11px] text-muted-foreground">请扫描下方微信收款码完成赞助，赞助截图需备注您的用户PID，提交后管理员审核通过将为您发放对应Token。</p>

            {/* WeChat QR Code */}
            <div className="flex flex-col items-center gap-3 p-4 rounded-xl bg-accent/30 border" style={{ borderColor: "hsl(var(--border))" }}>
              <span className="text-xs font-medium text-muted-foreground">微信收款码</span>
              <img
                src="/uploads/Storepic/weixinPic.png"
                alt="微信收款码"
                className="w-[180px] h-[180px] object-contain rounded-xl"
                onError={(e) => {
                  (e.target as HTMLImageElement).style.display = "none";
                }}
              />
              <span className="text-[10px] text-muted-foreground">请使用微信扫描二维码赞助</span>
            </div>

            {/* Upload screenshot */}
            <div
              className="border-2 border-dashed rounded-xl p-6 text-center cursor-pointer hover:border-primary/50 transition-colors"
              style={{ borderColor: "hsl(var(--border))" }}
              onClick={() => document.getElementById("walletSponsorFileInput")?.click()}
            >
              {sponsorPreview ? (
                <img src={sponsorPreview} alt="预览" className="max-h-[180px] mx-auto rounded-lg" />
              ) : (
                <div className="space-y-2">
                  <Camera size={28} className="mx-auto text-muted-foreground" />
                  <p className="text-xs text-muted-foreground">点击上传赞助截图</p>
                  <p className="text-[10px] text-muted-foreground/50">支持 PNG / JPG / GIF</p>
                </div>
              )}
              <input id="walletSponsorFileInput" type="file" accept="image/*" className="hidden" onChange={handleSponsorFileChange} />
            </div>

            <div className="space-y-1.5">
              <label className="text-xs text-muted-foreground">赞助金额（元）</label>
              <input
                type="number"
                value={sponsorAmount}
                onChange={(e) => setSponsorAmount(e.target.value)}
                placeholder="请输入赞助金额"
                step="0.01" min="0.01"
                className="w-full text-xs px-3 py-2 rounded-lg border bg-transparent outline-none"
                style={{ borderColor: "hsl(var(--border))" }}
              />
            </div>

            {sponsorError && <p className="text-[11px] text-destructive">{sponsorError}</p>}

            <button
              onClick={handleSponsorSubmit}
              disabled={sponsorSubmitting}
              className="w-full py-2.5 rounded-lg bg-primary text-primary-foreground text-xs font-medium hover:opacity-90 disabled:opacity-50 flex items-center justify-center gap-2"
            >
              <Upload size={13} />
              {sponsorSubmitting ? "提交中..." : "创建赞助审核"}
            </button>
          </div>
        ) : (
          /* Wallet main view */
          <div className="p-5 space-y-5">
            {/* Balance */}
            <div className="text-center py-6">
              <p className="text-xs text-muted-foreground mb-2">当前余额</p>
              <p className="text-3xl font-bold flex items-center justify-center gap-2" style={{ fontFamily: "'Geist Mono', monospace" }}>
                <Coins size={22} className="text-[#d4839a]" />
                {balance}
              </p>
              <div className="mt-4 flex items-center justify-center gap-3">
                <button onClick={() => setShowSponsor(true)}
                  className="inline-flex items-center gap-2 px-6 py-2.5 rounded-xl bg-primary text-primary-foreground text-xs font-medium hover:opacity-90">
                  <Heart size={14} /> 赞助
                </button>
                <button
                  onClick={async () => {
                    setCheckinLoading(true);
                    try {
                      await onCheckin();
                      alert("签到成功！获得 ¥0.2500");
                    } catch (e: unknown) {
                      alert(e instanceof Error ? e.message : "签到失败");
                    } finally {
                      setCheckinLoading(false);
                    }
                  }}
                  disabled={checkedIn || checkinLoading}
                  className={`inline-flex items-center gap-2 px-6 py-2.5 rounded-xl text-xs font-medium transition-colors ${
                    checkedIn
                      ? "bg-muted text-muted-foreground cursor-not-allowed"
                      : "bg-amber-500 text-white hover:bg-amber-600"
                  } disabled:opacity-60`}
                >
                  {checkedIn ? (
                    <><CheckCircle size={14} /> 已签到</>
                  ) : checkinLoading ? (
                    <><Loader2 size={14} className="animate-spin" /> 签到中</>
                  ) : (
                    <><CalendarCheck size={14} /> 签到</>
                  )}
                </button>
              </div>
            </div>

            {/* Usage Records */}
            <div className="space-y-2">
              <h4 className="text-xs font-medium text-muted-foreground">消费记录</h4>
              {usageRecords.length === 0 ? (
                <p className="text-[11px] text-muted-foreground/60">暂无消费记录</p>
              ) : (
                <div className="space-y-1 max-h-[200px] overflow-y-auto">
                  {usageRecords.map((r, i) => (
                    <div key={r.id || i} className="flex justify-between items-center py-1.5 px-2 rounded-lg text-[11px] hover:bg-accent/50">
                      <span className="text-muted-foreground">{r.modelName}</span>
                      <span className="text-foreground">{r.costAmount != null ? `-¥${Number(r.costAmount).toFixed(4)}` : ""}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
