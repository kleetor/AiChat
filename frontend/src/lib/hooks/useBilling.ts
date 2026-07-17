import { useState, useCallback } from "react";
import {
  getBilling,
  getUsageRecords,
  type BillingInfo,
  type TokenUsage,
} from "@/lib/services";

export function useBilling() {
  const [billingInfo, setBillingInfo] = useState<BillingInfo | null>(null);
  const [usageRecords, setUsageRecords] = useState<TokenUsage[]>([]);

  const loadBilling = useCallback(async () => {
    try {
      const [billing, usage] = await Promise.all([
        getBilling(),
        getUsageRecords(0, 20),
      ]);
      setBillingInfo(billing);
      setUsageRecords(usage.content || []);
    } catch (e) {
      console.warn("加载消费信息失败:", e);
    }
  }, []);

  return { billingInfo, usageRecords, loadBilling };
}
