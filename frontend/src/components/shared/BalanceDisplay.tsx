import { Coins } from "lucide-react";

interface BalanceDisplayProps {
  amount: string;
  onClick?: () => void;
}

export default function BalanceDisplay({ amount, onClick }: BalanceDisplayProps) {
  return (
    <span
      onClick={onClick}
      className={`inline-flex items-center gap-1 px-2 py-1 rounded-md text-xs tabular-nums shrink-0 ${
        onClick ? "cursor-pointer hover:opacity-80" : ""
      }`}
      style={{
        background: "linear-gradient(135deg, rgba(212,131,154,0.1), rgba(200,120,140,0.06))",
        color: "#d4839a",
        fontFamily: "'Geist Mono', monospace",
      }}
      title={onClick ? "点击查看钱包" : "账户余额"}
    >
      <Coins size={13} strokeWidth={2} />
      {amount}
    </span>
  );
}
