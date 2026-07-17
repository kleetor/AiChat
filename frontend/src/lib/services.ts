import { apiGet, apiPost, apiPut, apiDelete, apiPostForm, apiStream } from "./api";

// ---- Types ----

export interface Conversation {
  id: number;
  title: string;
  createdAt: string;
  updatedAt: string;
  userId: number;
}

export interface ChatMessage {
  id: number;
  role: "user" | "assistant" | "system";
  content: string;
  createdAt?: string;
  isLocal?: boolean;
}

export interface ChatRequest {
  message: string;
  promptId?: number | null;
  modelConfigId?: number | null;
  webSearchEnabled?: boolean;
  imageDescription?: string | null;
  knowledgeBaseId?: number | null;
  longMemoryEnabled?: boolean;
}

export interface ChatResponse {
  reply: string;
  inputTokens: number;
  outputTokens: number;
  costAmount: number;
}

export interface ModelConfig {
  id: number;
  apiUrl: string;
  modelName: string;
}

export interface Prompt {
  id: number;
  name: string;
  content: string;
  createdAt?: string;
}

export interface KnowledgeBase {
  id: number;
  name: string;
  description: string;
  docCount?: number;
  chunkCount?: number;
}

export interface BillingInfo {
  balance: number;
  totalSpent: number;
  totalTokens: number;
}

export interface TokenUsage {
  id: number;
  modelName: string;
  inputTokens: number;
  outputTokens: number;
  costAmount: number;
  createdAt: string;
}

export interface Notification {
  id: number;
  type: string;
  title: string;
  content: string;
  isRead: boolean;
  createdAt: string;
  fromUserName?: string;
}

export interface FriendInfo {
  userId: number;
  username: string;
  avatarUrl?: string;
  friendshipId: number;
}

export interface FriendRequest {
  friendshipId: number;
  fromUserId: number;
  fromUsername: string;
  createdAt: string;
}

export interface FriendMessage {
  id: number;
  senderId: number;
  content: string;
  createdAt: string;
}

// ---- Conversations ----

export function getConversations(): Promise<Conversation[]> {
  return apiGet("/api/conversations");
}

export function createConversation(): Promise<Conversation> {
  return apiPost("/api/conversations");
}

export function deleteConversation(id: number): Promise<void> {
  return apiDelete(`/api/conversations/${id}`);
}

// ---- Chat ----

export function sendMessage(conversationId: number, req: ChatRequest): Promise<ChatResponse> {
  return apiPost(`/api/chat/${conversationId}`, req);
}

export function streamMessage(conversationId: number, req: ChatRequest) {
  return apiStream(`/api/chat/${conversationId}/stream`, req);
}

export interface ChatHistoryResponse {
  conversationId: number;
  messages: ChatMessage[];
}

/** 后端返回的原始历史消息格式 */
interface RawMessageRecord {
  id: number;
  userMessage: string;
  aiReply: string;
  timestamp: string;
}

export async function getChatHistory(conversationId: number): Promise<ChatHistoryResponse> {
  const raw = await apiGet<{ conversationId: number; messages: RawMessageRecord[] }>(
    `/api/chat/${conversationId}/history`,
  );
  const messages: ChatMessage[] = [];
  for (const r of (raw.messages || [])) {
    messages.push({
      id: r.id * 2 - 1,
      role: "user",
      content: r.userMessage || "",
      createdAt: r.timestamp,
    });
    messages.push({
      id: r.id * 2,
      role: "assistant",
      content: r.aiReply || "",
      createdAt: r.timestamp,
    });
  }
  return { conversationId: raw.conversationId, messages };
}

export function deleteChatMessage(id: number): Promise<void> {
  return apiDelete(`/api/chat/messages/${id}`);
}

// ---- Model Configs ----

export function getModelConfigs(): Promise<ModelConfig[]> {
  return apiGet("/api/model-configs");
}

// ---- Prompts ----

export function getPrompts(): Promise<Prompt[]> {
  return apiGet("/api/prompts");
}

export function createPrompt(name: string, content: string): Promise<Prompt> {
  return apiPost("/api/prompts", { name, content });
}

export function updatePrompt(id: number, name: string, content: string): Promise<Prompt> {
  return apiPut(`/api/prompts/${id}`, { name, content });
}

export function deletePrompt(id: number): Promise<void> {
  return apiDelete(`/api/prompts/${id}`);
}

// ---- Image ----

export function uploadImage(file: File): Promise<{ imageUrl: string; description: string }> {
  const fd = new FormData();
  fd.append("file", file);
  return apiPostForm("/api/image/upload", fd);
}

// ---- Knowledge Base ----

export function getKBList(): Promise<KnowledgeBase[]> {
  return apiGet("/api/kb/list");
}

export function createKB(name: string, description: string): Promise<KnowledgeBase> {
  return apiPost("/api/kb/create", { name, description });
}

export function updateKB(id: number, name: string, description: string): Promise<KnowledgeBase> {
  return apiPut(`/api/kb/${id}`, { name, description });
}

export function deleteKB(id: number): Promise<void> {
  return apiDelete(`/api/kb/${id}`);
}

export interface KbDocument {
  id: number;
  kbId: number;
  fileName: string;
  fileType: string;
  fileSize: number;
  status: "PROCESSING" | "READY" | "ERROR";
  chunkCount: number;
  errorMsg?: string;
  createdAt: string;
}

export function getKBDocuments(kbId: number): Promise<KbDocument[]> {
  return apiGet(`/api/kb/${kbId}/docs`);
}

export function uploadKBDocument(kbId: number, file: File): Promise<KbDocument> {
  const fd = new FormData();
  fd.append("file", file);
  return apiPostForm(`/api/kb/${kbId}/docs/upload`, fd);
}

export function deleteKBDocument(docId: number): Promise<void> {
  return apiDelete(`/api/kb/docs/${docId}`);
}

export function reindexKBDocument(docId: number): Promise<{ message: string }> {
  return apiPost(`/api/kb/docs/${docId}/reindex`);
}

// ---- Billing ----

export function getBilling(): Promise<BillingInfo> {
  return apiGet("/api/billing/balance");
}

export function getUsageRecords(page: number, size: number): Promise<{ content: TokenUsage[]; totalPages: number }> {
  return apiGet(`/api/billing/usage-records?page=${page}&size=${size}`);
}

export function sponsorCreate(file: File, amount: number): Promise<{ success: boolean; message: string }> {
  const fd = new FormData();
  fd.append("image", file);
  fd.append("amount", String(amount));
  return apiPostForm("/api/billing/sponsor-create", fd);
}

// ---- Notifications ----

export function getNotifications(): Promise<Notification[]> {
  return apiGet("/api/notifications");
}

export function getUnreadNotificationCount(): Promise<{ count: number }> {
  return apiGet("/api/notifications/unread-count");
}

export function markAllNotificationsRead(): Promise<void> {
  return apiPost("/api/notifications/read-all");
}

export function markNotificationRead(id: number): Promise<void> {
  return apiPost(`/api/notifications/${id}/read`);
}

export function deleteNotification(id: number): Promise<void> {
  return apiDelete(`/api/notifications/${id}`);
}

// ---- Friends ----

export function getFriendList(): Promise<FriendInfo[]> {
  return apiGet("/api/friends/list");
}

export function searchUsers(keyword: string): Promise<{ userId: number; username: string; pid: string }[]> {
  return apiGet(`/api/friends/search?keyword=${encodeURIComponent(keyword)}`);
}

export function sendFriendRequest(userId: number): Promise<{ message: string }> {
  return apiPost("/api/friends/request", { userId });
}

export function getPendingFriendRequests(): Promise<FriendRequest[]> {
  return apiGet("/api/friends/pending");
}

export function acceptFriendRequest(friendshipId: number): Promise<{ message: string }> {
  return apiPost("/api/friends/accept", { friendshipId });
}

export function rejectFriendRequest(friendshipId: number): Promise<{ message: string }> {
  return apiPost("/api/friends/reject", { friendshipId });
}

export function getFriendChatHistory(friendUserId: number): Promise<FriendMessage[]> {
  return apiGet(`/api/friends/chat/${friendUserId}`);
}

export function sendFriendMessage(friendshipId: number, content: string): Promise<FriendMessage> {
  return apiPost("/api/friends/message", { friendshipId, content });
}

export function markFriendMessagesRead(senderId: number): Promise<void> {
  return apiPost(`/api/friends/read/${senderId}`);
}

// ---- Memory ----

export interface MemoryItem {
  id: number;
  userId: number;
  chromaId: string;
  value: string;
  originalValue?: string;
  detailLevel: "FULL" | "BRIEF" | "TITLE";
  source: "MANUAL" | "AUTO";
  lastAccessedAt: string;
  accessCount: number;
  conversationId?: number;
  enabled: boolean;
  createdAt: string;
}

export function getMemoryList(): Promise<MemoryItem[]> {
  return apiGet("/api/memory/list");
}

export function getMemoryEnabled(): Promise<MemoryItem[]> {
  return apiGet("/api/memory/enabled");
}

export function addMemory(value: string): Promise<MemoryItem> {
  return apiPost("/api/memory/add", { value });
}

export function updateMemory(id: number, value: string): Promise<void> {
  return apiPut(`/api/memory/${id}`, { value });
}

export function toggleMemory(id: number, enabled: boolean): Promise<void> {
  return apiPut(`/api/memory/${id}/toggle?enabled=${enabled}`);
}

export function deleteMemory(id: number): Promise<void> {
  return apiDelete(`/api/memory/${id}`);
}

export function clearMemories(): Promise<void> {
  return apiDelete("/api/memory/clear");
}

export function searchMemories(query: string): Promise<MemoryItem[]> {
  return apiPost("/api/memory/search", { query });
}
