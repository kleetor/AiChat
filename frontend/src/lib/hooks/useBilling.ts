import { useState, useCallback } from "react";
import {
  getBilling,
  getUsageRecords,
  getCheckinStatus,
  dailyCheckin,
  type BillingInfo,
  type TokenUsage,
} from "@/lib/services";

export function useBilling() {
  const [billingInfo, setBillingInfo] = useState<BillingInfo | null>(null);
  const [usageRecords, setUsageRecords] = useState<TokenUsage[]>([]);
  const [checkedIn, setCheckedIn] = useState(false);

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

  const loadCheckinStatus = useCallback(async () => {
    try {
      const status = await getCheckinStatus();
      setCheckedIn(status.checkedIn);
    } catch (e) {
      console.warn("加载签到状态失败:", e);
    }
  }, []);

  const checkin = useCallback(async (): Promise<boolean> => {
    try {
      const result = await dailyCheckin();
      if (result.success) {
        setCheckedIn(true);
        return true;
      }
      return false;
    } catch (e: unknown) {
      throw e;
    }
  }, []);

  return { billingInfo, usageRecords, checkedIn, loadBilling, loadCheckinStatus, checkin };
}
