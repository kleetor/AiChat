import { X, Search, UserPlus, Mail, ChevronLeft, Send, Check, X as XIcon } from "lucide-react";
import { useState, useRef, useEffect, useCallback } from "react";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import type { FriendInfo, FriendRequest, FriendMessage } from "@/lib/services";

interface FriendModalProps {
  open: boolean;
  onClose: () => void;
  friends: FriendInfo[];
  onSearchUsers: (query: string) => Promise<{ userId: number; username: string; pid: string }[]>;
  onSendFriendRequest: (userId: number) => Promise<void>;
  friendRequests: FriendRequest[];
  onAcceptRequest: (friendshipId: number) => Promise<void>;
  onRejectRequest: (friendshipId: number) => Promise<void>;
  onLoadChatHistory: (friendUserId: number) => Promise<FriendMessage[]>;
  onSendMessage: (friendshipId: number, content: string) => Promise<FriendMessage>;
}

export default function FriendModal({
  open,
  onClose,
  friends,
  onSearchUsers,
  onSendFriendRequest,
  friendRequests,
  onAcceptRequest,
  onRejectRequest,
  onLoadChatHistory,
  onSendMessage,
}: FriendModalProps) {
  const [searchQuery, setSearchQuery] = useState("");
  const [searchResults, setSearchResults] = useState<{ userId: number; username: string; pid: string }[]>([]);
  const [searching, setSearching] = useState(false);
  const [showRequests, setShowRequests] = useState(false);

  const [selectedFriend, setSelectedFriend] = useState<FriendInfo | null>(null);
  const [chatMessages, setChatMessages] = useState<FriendMessage[]>([]);
  const [chatInput, setChatInput] = useState("");
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState("");
  const chatEndRef = useRef<HTMLDivElement>(null);
  const searchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    return () => {
      if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
    };
  }, []);

  const handleSearch = useCallback(() => {
    if (!searchQuery.trim()) return;
    if (searchTimerRef.current) clearTimeout(searchTimerRef.current);
    searchTimerRef.current = setTimeout(async () => {
      setSearching(true);
      const results = await onSearchUsers(searchQuery.trim());
      setSearchResults(results);
      setSearching(false);
    }, 300);
  }, [searchQuery, onSearchUsers]);

  // Trigger debounced search on query change
  useEffect(() => {
    if (searchQuery.trim().length >= 2) {
      handleSearch();
    } else {
      setSearchResults([]);
    }
  }, [searchQuery, handleSearch]);

  // 自动滚动到聊天底部
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [chatMessages]);

  if (!open) return null;

  const handleSelectFriend = async (friend: FriendInfo) => {
    setSelectedFriend(friend);
    setShowRequests(false);
    const msgs = await onLoadChatHistory(friend.userId);
    setChatMessages(msgs);
  };

  const handleSendMessage = async () => {
    if (!chatInput.trim() || !selectedFriend) return;
    setSending(true);
    setSendError("");
    try {
      const msg = await onSendMessage(selectedFriend.friendshipId, chatInput.trim());
      setChatMessages(prev => [...prev, msg]);
      setChatInput("");
    } catch (e: unknown) {
      setSendError(e instanceof Error ? e.message : "发送失败");
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="fixed inset-0 bg-black/40" onClick={onClose} />
      <div className="relative bg-background rounded-2xl shadow-2xl w-full max-w-[640px] h-[70vh] mx-4 border border-border flex overflow-hidden">
        {/* Left sidebar */}
        <div className={`${selectedFriend ? "hidden md:flex" : "flex"} flex-col w-full md:w-56 border-r border-border shrink-0`}>
          <div className="flex items-center justify-between px-4 py-3 border-b border-border shrink-0">
            <h2 className="text-base font-semibold">好友列表</h2>
            <button onClick={onClose} className="p-1 rounded-md text-muted-foreground hover:bg-accent hover:text-foreground md:hidden"><X size={16} /></button>
          </div>

          {/* Search */}
          <div className="p-3 border-b border-border">
            <div className="flex gap-1.5">
              <input
                type="text" value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleSearch()}
                placeholder="输入用户名或PID搜索..."
                className="flex-1 text-[11px] px-2.5 py-1.5 rounded-md border bg-transparent outline-none"
                style={{ borderColor: "hsl(var(--border))" }}
              />
              <button onClick={handleSearch} disabled={searching}
                className="px-2.5 py-1.5 rounded-md bg-primary text-primary-foreground text-[11px] font-medium shrink-0 hover:opacity-90">
                <Search size={12} />
              </button>
            </div>
            {/* Search results */}
            {searchResults.length > 0 && (
              <div className="mt-2 space-y-0.5 border rounded-lg p-1" style={{ borderColor: "hsl(var(--border))" }}>
                {searchResults.map((u) => (
                  <button key={u.userId} onClick={async () => { await onSendFriendRequest(u.userId); setSearchResults([]); setSearchQuery(""); }}
                    className="w-full text-left px-2 py-1.5 rounded text-[11px] hover:bg-accent flex items-center justify-between">
                    <span>{u.username} ({u.pid})</span>
                    <UserPlus size={11} className="text-primary" />
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Friends list */}
          <div className="flex-1 overflow-y-auto p-2 space-y-0.5">
            {friends.length === 0 ? (
              <p className="text-[11px] text-muted-foreground/60 text-center py-6">暂无好友，去搜索添加吧</p>
            ) : (
              friends.map((f) => (
                <button key={f.userId}
                  onClick={() => handleSelectFriend(f)}
                  className={`w-full text-left px-3 py-2 rounded-lg text-xs hover:bg-accent transition-colors flex items-center gap-2.5 ${
                    selectedFriend?.userId === f.userId ? "bg-accent text-foreground" : "text-foreground"
                  }`}>
                  <Avatar className="h-7 w-7">
                    <AvatarImage src={f.avatarUrl} alt={f.username} />
                    <AvatarFallback className="text-[10px]">{f.username.charAt(0)}</AvatarFallback>
                  </Avatar>
                  {f.username}
                </button>
              ))
            )}
          </div>

          {/* Footer */}
          <div className="p-3 border-t border-border flex gap-2 shrink-0">
            <button onClick={() => { setShowRequests(true); }}
              className="flex-1 flex items-center justify-center gap-1 py-2 rounded-lg border text-[11px] text-muted-foreground hover:bg-accent relative"
              style={{ borderColor: "hsl(var(--border))" }}>
              <Mail size={12} /> 申请
              {friendRequests.length > 0 && (
                <span className="absolute -top-1 -right-1 w-4 h-4 rounded-full bg-destructive text-destructive-foreground text-[9px] flex items-center justify-center">
                  {friendRequests.length}
                </span>
              )}
            </button>
          </div>

          {/* Friend requests panel */}
          {showRequests && (
            <div className="border-t border-border p-3 space-y-2">
              <p className="text-[10px] font-medium text-muted-foreground uppercase tracking-wider">好友申请</p>
              {friendRequests.length === 0 ? (
                <p className="text-[11px] text-muted-foreground/60">暂无待处理的申请</p>
              ) : (
                friendRequests.map((req) => (
                  <div key={req.friendshipId} className="flex items-center justify-between gap-2">
                    <span className="text-[11px]">{req.fromUsername}</span>
                    <div className="flex gap-1">
                      <button onClick={() => onAcceptRequest(req.friendshipId)}
                        className="p-1 rounded text-green-600 hover:bg-green-50"><Check size={12} /></button>
                      <button onClick={() => onRejectRequest(req.friendshipId)}
                        className="p-1 rounded text-destructive hover:bg-red-50"><XIcon size={12} /></button>
                    </div>
                  </div>
                ))
              )}
              <button onClick={() => setShowRequests(false)} className="w-full text-[10px] text-muted-foreground text-center">收起</button>
            </div>
          )}
        </div>

        {/* Chat area */}
        <div className={`${selectedFriend ? "flex" : "hidden md:flex"} flex-col flex-1`}>
          {selectedFriend ? (
            <>
              <div className="flex items-center gap-2 px-4 py-3 border-b border-border shrink-0">
                <button onClick={() => setSelectedFriend(null)}
                  className="p-1 rounded-md text-muted-foreground hover:bg-accent hover:text-foreground md:hidden"><ChevronLeft size={16} /></button>
                <Avatar className="h-7 w-7">
                  <AvatarImage src={selectedFriend.avatarUrl} alt={selectedFriend.username} />
                  <AvatarFallback className="text-[10px]">{selectedFriend.username.charAt(0)}</AvatarFallback>
                </Avatar>
                <span className="text-xs font-medium">{selectedFriend.username}</span>
              </div>
              <div className="flex-1 overflow-y-auto p-4 space-y-3">
                {chatMessages.length === 0 ? (
                  <p className="text-[11px] text-muted-foreground/60 text-center pt-10">暂无消息，开始聊天吧</p>
                ) : (
                  chatMessages.map((msg) => (
                    <div key={msg.id} className={`flex ${msg.senderId === selectedFriend.userId ? "justify-start" : "justify-end"}`}>
                      <div className={`max-w-[75%] px-3 py-2 rounded-xl text-xs ${
                        msg.senderId === selectedFriend.userId
                          ? "bg-accent text-foreground rounded-bl-md"
                          : "bg-primary text-primary-foreground rounded-br-md"
                      }`}>
                        {msg.content}
                      </div>
                    </div>
                  ))
                )}
                <div ref={chatEndRef} />
              </div>
              <div className="p-3 border-t border-border flex gap-2 shrink-0">
                <input
                  type="text" value={chatInput} onChange={(e) => setChatInput(e.target.value)}
                  onKeyDown={(e) => e.key === "Enter" && handleSendMessage()}
                  placeholder="输入消息..."
                  className="flex-1 text-xs px-3 py-2 rounded-lg border bg-transparent outline-none"
                  style={{ borderColor: "hsl(var(--border))" }}
                />
                <button onClick={handleSendMessage} disabled={sending || !chatInput.trim()}
                  className="p-2 rounded-lg bg-primary text-primary-foreground hover:opacity-90 disabled:opacity-50">
                  <Send size={13} />
                </button>
              </div>
              {sendError && <p className="px-3 pb-2 text-[11px] text-destructive">{sendError}</p>}
            </>
          ) : (
            <div className="flex-1 flex items-center justify-center">
              <p className="text-xs text-muted-foreground">选择一个好友开始聊天</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
