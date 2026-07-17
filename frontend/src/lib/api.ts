const TOKEN_KEY = "chat_token";

/**
 * Token 存储策略：使用 sessionStorage 替代 localStorage。
 * sessionStorage 仅在当前标签页有效，关闭标签页即清除，减少持久化攻击面。
 * 理想方案是后端使用 httpOnly cookie，此处为前后端分离折中方案。
 */
export function getToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  sessionStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  sessionStorage.removeItem(TOKEN_KEY);
}

export class ApiError extends Error {
  status: number;
  body: unknown;

  constructor(status: number, body: unknown) {
    const msg = typeof body === "object" && body && "message" in body
      ? String((body as Record<string, unknown>).message)
      : String(body);
    super(msg);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

async function request<T>(
  method: string,
  url: string,
  body?: unknown,
  isFormData?: boolean,
): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {};
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  if (!isFormData) {
    headers["Content-Type"] = "application/json";
  }

  const res = await fetch(url, {
    method,
    headers,
    body: isFormData ? (body as FormData) : body ? JSON.stringify(body) : undefined,
  });

  if (!res.ok) {
    let errorBody: unknown;
    try {
      errorBody = await res.json();
    } catch {
      errorBody = await res.text();
    }
    if (res.status === 401) {
      clearToken();
      if (!window.location.pathname.includes("/login")) {
        window.location.href = "/login";
      }
    }
    throw new ApiError(res.status, errorBody);
  }

  if (res.status === 204) return undefined as T;

  const text = await res.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

export function apiGet<T>(url: string): Promise<T> {
  return request<T>("GET", url);
}

export function apiPost<T>(url: string, body?: unknown): Promise<T> {
  return request<T>("POST", url, body);
}

export function apiPut<T>(url: string, body?: unknown): Promise<T> {
  return request<T>("PUT", url, body);
}

export function apiDelete<T>(url: string): Promise<T> {
  return request<T>("DELETE", url);
}

export function apiPostForm<T>(url: string, formData: FormData): Promise<T> {
  return request<T>("POST", url, formData, true);
}

/**
 * SSE streaming request - returns an AbortController + async generator
 */
export function apiStream(
  url: string,
  body: unknown,
): { controller: AbortController; stream: AsyncGenerator<string, void, unknown> } {
  const controller = new AbortController();
  const token = getToken();

  async function* streamGenerator(): AsyncGenerator<string, void, unknown> {
    const res = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    });

    if (!res.ok) {
      let errorBody: unknown;
      try { errorBody = await res.json(); } catch { errorBody = await res.text(); }
      if (res.status === 401) {
        clearToken();
        if (!window.location.pathname.includes("/login")) {
          window.location.href = "/login";
        }
      }
      throw new ApiError(res.status, errorBody);
    }

    const reader = res.body?.getReader();
    if (!reader) throw new Error("No response body");

    const decoder = new TextDecoder();
    let buffer = "";

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split("\n");
        buffer = lines.pop() || "";

        for (const line of lines) {
          const trimmed = line.trim();
          if (trimmed.startsWith("data:")) {
            const data = trimmed.slice(5).trim();
            if (data.trim().toUpperCase() === "[DONE]") return;
            try {
              const parsed = JSON.parse(data);
              if (parsed.content) {
                yield parsed.content;
              }
            } catch {
              yield data;
            }
          }
        }
      }
    } finally {
      reader.releaseLock();
    }
  }

  return { controller, stream: streamGenerator() };
}
