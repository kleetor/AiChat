import { createContext, useContext, useState, useEffect, useCallback, type ReactNode } from "react";
import { apiGet, apiPost, apiPostForm, getToken, setToken, clearToken, ApiError } from "@/lib/api";

export interface UserInfo {
  username: string;
  email: string;
  pid: string;
  signature: string;
  avatarUrl: string;
  balance: number;
}

interface AuthContextType {
  isLoggedIn: boolean;
  isLoading: boolean;
  user: UserInfo | null;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, code: string) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
  updateSignature: (signature: string) => Promise<void>;
  uploadAvatar: (file: File) => Promise<string>;
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>;
  verifyPassword: (password: string) => Promise<boolean>;
  sendVerificationCode: (email: string) => Promise<void>;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserInfo | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const refreshUser = useCallback(async () => {
    if (!getToken()) {
      setUser(null);
      setIsLoading(false);
      return;
    }
    try {
      const data = await apiGet<Record<string, unknown>>("/api/auth/me");
      setUser({
        username: String(data.username || ""),
        email: String(data.email || ""),
        pid: String(data.pid || ""),
        signature: String(data.signature || ""),
        avatarUrl: String(data.avatarUrl || ""),
        balance: Number(data.balance || 0),
      });
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) {
        clearToken();
      }
      setUser(null);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshUser();
  }, [refreshUser]);

  const login = async (email: string, password: string) => {
    const data = await apiPost<{ token: string; username: string }>("/api/auth/login", { email, password });
    setToken(data.token);
    await refreshUser();
  };

  const register = async (email: string, password: string, code: string) => {
    const data = await apiPost<{ token: string; username: string; balance: number }>("/api/auth/register", {
      email,
      password,
      code,
    });
    setToken(data.token);
    await refreshUser();
  };

  const logout = () => {
    clearToken();
    setUser(null);
    window.location.href = "/login";
  };

  const updateSignature = async (signature: string) => {
    await apiPost("/api/auth/update-profile", { signature });
    setUser(prev => prev ? { ...prev, signature } : null);
  };

  const uploadAvatar = async (file: File): Promise<string> => {
    const formData = new FormData();
    formData.append("file", file);
    const data = await apiPostForm<{ avatarUrl: string }>("/api/auth/upload-avatar", formData);
    setUser(prev => prev ? { ...prev, avatarUrl: data.avatarUrl } : null);
    return data.avatarUrl;
  };

  const changePassword = async (currentPassword: string, newPassword: string) => {
    await apiPost("/api/auth/change-password", { currentPassword, newPassword });
  };

  const verifyPassword = async (password: string): Promise<boolean> => {
    try {
      await apiPost("/api/auth/verify-password", { password });
      return true;
    } catch {
      return false;
    }
  };

  const sendVerificationCode = async (email: string) => {
    await apiPost("/api/auth/send-code", { email });
  };

  return (
    <AuthContext.Provider
      value={{
        isLoggedIn: !!user,
        isLoading,
        user,
        login,
        register,
        logout,
        refreshUser,
        updateSignature,
        uploadAvatar,
        changePassword,
        verifyPassword,
        sendVerificationCode,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
