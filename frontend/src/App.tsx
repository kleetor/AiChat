import { useState, useEffect, useCallback } from "react";
import Sidebar from "@/components/layout/Sidebar";
import Header from "@/components/layout/Header";
import InputBar from "@/components/layout/InputBar";
import WelcomeScreen from "@/components/chat/WelcomeScreen";
import { useToast } from "@/lib/toast";
import ChatMessages from "@/components/chat/ChatMessages";
import WalletModal from "@/components/modals/WalletModal";
import ProfileModal from "@/components/modals/ProfileModal";
import PromptModal from "@/components/modals/PromptModal";
import MessageModal from "@/components/modals/MessageModal";
import FriendModal from "@/components/modals/FriendModal";
import KBModal from "@/components/modals/KBModal";
import MemoryModal from "@/components/modals/MemoryModal";
import { useAuth } from "@/lib/auth";
import { useConversations } from "@/lib/hooks/useConversations";
import { useChat } from "@/lib/hooks/useChat";
import { useNotifications } from "@/lib/hooks/useNotifications";
import { useFriends } from "@/lib/hooks/useFriends";
import { useBilling } from "@/lib/hooks/useBilling";
import { useImageUpload } from "@/lib/hooks/useImageUpload";
import { useFileUpload } from "@/lib/hooks/useFileUpload";
import {
  getChatHistory,
  getModelConfigs,
  getPrompts,
  createPrompt,
  updatePrompt,
  deletePrompt,
  uploadImage,
  uploadFile,
  getKBList,
  sponsorCreate,
  deleteChatMessage as deleteChatMsg,
  type ChatMessage as ChatMessageType,
  type ModelConfig,
  type Prompt,
  type KnowledgeBase,
} from "@/lib/services";

type ModalType = "profile" | "wallet" | "prompt" | "message" | "friend" | "kb" | "memory" | null;

export default function App() {
  const auth = useAuth();
  const { isLoggedIn, isLoading: authLoading } = auth;
  const { toast } = useToast();

  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [inputValue, setInputValue] = useState("");
  const [messages, setMessages] = useState<ChatMessageType[]>([]);
  const [activeModal, setActiveModal] = useState<ModalType>(null);
  const [webSearchEnabled, setWebSearchEnabled] = useState(false);

  // Models
  const [modelConfigs, setModelConfigs] = useState<ModelConfig[]>([]);
  const [selectedModelId, setSelectedModelId] = useState<number | null>(null);

  // Prompts
  const [prompts, setPrompts] = useState<Prompt[]>([]);
  const [activePrompt, setActivePrompt] = useState<Prompt | null>(null);

  // KB
  const [kbList, setKbList] = useState<KnowledgeBase[]>([]);
  const [selectedKBId, setSelectedKBId] = useState<number | null>(null);

  // Custom hooks
  const conv = useConversations();
  const chat = useChat((msg) => toast("error", msg));
  const notif = useNotifications();
  const friends = useFriends();
  const billing = useBilling();
  const img = useImageUpload((msg) => toast("error", msg));
  const file = useFileUpload((msg) => toast("error", msg));

  // ---- Load data after login ----
  useEffect(() => {
    if (!isLoggedIn) return;
    Promise.all([
      conv.loadConversations(),
      loadModelConfigs(),
      loadPrompts(),
      loadKBList(),
      billing.loadBilling(),
      billing.loadCheckinStatus(),
      notif.loadNotifications(),
      notif.loadUnreadCount(),
      friends.loadFriends(),
    ]);
  }, [isLoggedIn]);

  // ---- Models ----
  const loadModelConfigs = async () => {
    try {
      const data = await getModelConfigs();
      setModelConfigs(data);
      if (data.length > 0 && !selectedModelId) {
        setSelectedModelId(data[0].id);
      }
    } catch (e) {
      console.warn("加载模型配置失败:", e);
    }
  };

  // ---- Prompts ----
  const loadPrompts = async () => {
    try {
      const data = await getPrompts();
      setPrompts(data);
    } catch (e) {
      console.warn("加载提示词失败:", e);
    }
  };

  // ---- KB ----
  const loadKBList = async () => {
    try {
      const data = await getKBList();
      setKbList(data);
    } catch (e) {
      console.warn("加载知识库失败:", e);
    }
  };

  // ---- Conversation selection ----
  const handleSelectConv = async (id: number) => {
    const msgs = await conv.handleSelectConv(id, async (convId) => {
      try {
        const history = await getChatHistory(convId);
        return history.messages || [];
      } catch (e) {
        console.warn("加载历史消息失败:", e);
        return [];
      }
    });
    if (msgs !== null) {
      setMessages(msgs);
    }
  };

  const handleDeleteConv = async (id: number) => {
    const deletedId = await conv.handleDeleteConv(id);
    if (conv.activeConvId === deletedId) {
      conv.setActiveConvId(null);
      setMessages([]);
    }
  };

  const handleNewChat = async () => {
    try {
      const newConv = await conv.handleNewChat();
      if (newConv) setMessages([]);
      setInputValue("");
    } catch {
      toast("error", "创建会话失败，请稍后重试");
    }
  };

  // ---- Chat Send ----
  const handleSend = useCallback(async () => {
    if (!inputValue.trim() || !isLoggedIn || !selectedModelId) return;

    const req = {
      message: inputValue.trim(),
      modelConfigId: selectedModelId,
      promptId: activePrompt?.id || null,
      webSearchEnabled,
      knowledgeBaseId: selectedKBId,
      longMemoryEnabled: true,
      imageDescription: img.imageDescriptionRef.current,
      fileUrl: file.fileUrlRef.current,
    };

    img.clearImageUpload();
    file.clearFileUpload();

    // 立即清空输入框，回复生成期间显示灰色不可编辑
    setInputValue("");

    await chat.handleSend(
      req,
      conv.activeConvId,
      conv.setActiveConvId,
      setMessages,
      (_) => {},
      inputValue.trim(),
    );

    // 流结束后重载历史，用数据库 ID 替换本地临时 ID，确保删除功能正常
    if (conv.activeConvId) {
      try {
        const history = await getChatHistory(conv.activeConvId);
        if (history.messages) setMessages(history.messages);
      } catch { /* ignore */ }
    }

    conv.loadConversations(); // refresh titles
  }, [inputValue, isLoggedIn, selectedModelId, activePrompt, webSearchEnabled, selectedKBId, chat, conv, img, file]);

  // ---- Tool click ----
  const handleToolClick = useCallback((id: string) => {
    setActiveModal(id as ModalType);
  }, []);

  // ---- Display helpers ----
  const modelDisplayOptions = modelConfigs.map((m, i) => ({
    id: String(m.id),
    name: m.modelName,
    tag: i === 0 ? "推荐" : "可用",
    color: ["#7c7cf8", "#a78bfa", "#34d399"][i % 3],
  }));

  const sidebarConvs = conv.conversations.map(c => ({
    id: c.id,
    title: c.title || "新建对话",
    preview: "",
    time: c.updatedAt ? new Date(c.updatedAt).toLocaleDateString() : "",
    active: c.id === conv.activeConvId,
  }));

  // ---- Loading state ----
  if (authLoading) {
    return (
      <div className="flex h-screen items-center justify-center bg-background">
        <div className="flex items-center gap-2">
          <span className="w-2 h-2 rounded-full bg-primary animate-bounce" style={{ animationDelay: "0ms" }} />
          <span className="w-2 h-2 rounded-full bg-primary animate-bounce" style={{ animationDelay: "150ms" }} />
          <span className="w-2 h-2 rounded-full bg-primary animate-bounce" style={{ animationDelay: "300ms" }} />
        </div>
      </div>
    );
  }

  const hasMessages = messages.length > 0;

  return (
    <div className="flex h-screen w-full overflow-hidden bg-background text-foreground">
      {/* Sidebar */}
      <Sidebar
        open={sidebarOpen}
        conversations={sidebarConvs}
        activeConv={conv.activeConvId || 0}
        onNewChat={handleNewChat}
        onSelectConv={handleSelectConv}
        onDeleteConv={handleDeleteConv}
        onClose={() => setSidebarOpen(false)}
      />

      {/* Main area */}
      <div className="flex flex-col flex-1 min-w-0 overflow-x-hidden">
        {/* Header */}
        <Header
          sidebarOpen={sidebarOpen}
          onToggleSidebar={() => setSidebarOpen(!sidebarOpen)}
          modelOptions={modelDisplayOptions}
          selectedModel={String(selectedModelId || "")}
          onSelectModel={(id) => setSelectedModelId(Number(id))}
          balance={billing.billingInfo ? `¥${billing.billingInfo.balance.toFixed(4)}` : "¥0.0000"}
          username={auth.user?.username || ""}
          isLoggedIn={isLoggedIn}
          avatarUrl={auth.user?.avatarUrl || ""}
          activePromptName={activePrompt?.name || null}
          onPromptClick={() => setActiveModal("prompt")}
          onRemovePrompt={() => setActivePrompt(null)}
          onToolClick={handleToolClick}
          onBalanceClick={() => setActiveModal("wallet")}
          onProfileClick={() => setActiveModal("profile")}
          onLogin={() => window.location.href = "/login"}
          onLogout={auth.logout}
          unreadNotificationCount={notif.unreadCount}
          kbOptions={[
            { value: "", label: "不使用" },
            ...kbList.map(k => ({ value: String(k.id), label: k.name })),
          ]}
          selectedKBId={String(selectedKBId || "")}
          onKBChange={(v) => setSelectedKBId(v ? Number(v) : null)}
        />

        {/* Chat body */}
        {hasMessages ? (
          <ChatMessages messages={messages} isGenerating={chat.isGenerating} onDeleteMessage={async (id: number) => {
            try {
              await deleteChatMsg(id);
              setMessages(prev => prev.filter(m => Math.abs(m.id) !== Math.abs(id)));
            } catch (e) {
              console.warn("删除消息失败:", e);
            }
          }} />
        ) : (
          <main className="flex-1 overflow-y-auto">
            <WelcomeScreen onActionClick={setInputValue} />
          </main>
        )}

        {/* Input bar */}
        <InputBar
          value={inputValue}
          onChange={setInputValue}
          onSend={handleSend}
          onStop={chat.handleStop}
          isGenerating={chat.isGenerating}
          disabled={!isLoggedIn || chat.isGenerating}
          onImageUpload={isLoggedIn ? (f) => img.handleImageUpload(f, uploadImage) : undefined}
          onClearImage={img.clearImageUpload}
          imageUploading={img.imageUploading}
          imagePreview={img.imagePreview}
          onFileUpload={isLoggedIn ? (f) => file.handleFileUpload(f, uploadFile) : undefined}
          onClearFile={file.clearFileUpload}
          fileUploading={file.fileUploading}
          fileName={file.fileName}
          webSearchEnabled={webSearchEnabled}
          onWebSearchChange={setWebSearchEnabled}
        />
      </div>

      {/* Modals */}
      <ProfileModal
        open={activeModal === "profile"}
        onClose={() => setActiveModal(null)}
        username={auth.user?.username || ""}
        email={auth.user?.email || ""}
        pid={auth.user?.pid || ""}
        signature={auth.user?.signature || ""}
        avatarUrl={auth.user?.avatarUrl || ""}
        onSaveSignature={auth.updateSignature}
        onChangePassword={auth.changePassword}
        onVerifyPassword={auth.verifyPassword}
        onAvatarUpload={auth.uploadAvatar}
      />

      <WalletModal
        open={activeModal === "wallet"}
        onClose={() => setActiveModal(null)}
        balance={billing.billingInfo ? `¥${billing.billingInfo.balance.toFixed(4)}` : "¥0.0000"}
        usageRecords={billing.usageRecords}
        onSponsor={async (file, amount) => {
          try {
            await sponsorCreate(file, amount);
            alert("赞助申请已提交，请等待管理员审核。");
            billing.loadBilling();
          } catch (e: unknown) {
            const msg = e instanceof Error ? e.message : "提交失败";
            alert(msg);
          }
        }}
        checkedIn={billing.checkedIn}
        onCheckin={async () => {
          await billing.checkin();
          billing.loadBilling();
        }}
      />

      <PromptModal
        open={activeModal === "prompt"}
        onClose={() => setActiveModal(null)}
        prompts={prompts}
        onSelectPrompt={(p) => { setActivePrompt(p); }}
        onDeletePrompt={async (id) => {
          try {
            await deletePrompt(id);
            if (activePrompt?.id === id) setActivePrompt(null);
            await loadPrompts();
          } catch (e) {
            console.warn("删除提示词失败:", e);
          }
        }}
        onNewPrompt={() => {}}
        onHubOpen={() => window.open("/workshop", "_blank")}
        onSavePrompt={async (name, content) => {
          try {
            await createPrompt(name, content);
            await loadPrompts();
          } catch (e) {
            console.warn("保存提示词失败:", e);
          }
        }}
        onUpdatePrompt={async (id, name, content) => {
          try {
            await updatePrompt(id, name, content);
            if (activePrompt?.id === id) {
              setActivePrompt({ id, name, content });
            }
            await loadPrompts();
          } catch (e) {
            console.warn("更新提示词失败:", e);
          }
        }}
      />

      <MessageModal
        open={activeModal === "message"}
        onClose={() => { setActiveModal(null); notif.loadUnreadCount(); }}
        messages={notif.notifications}
        onMarkAllRead={notif.handleMarkAllRead}
        onMarkOneRead={notif.handleMarkOneRead}
        onDelete={notif.handleDeleteNotification}
      />

      <FriendModal
        open={activeModal === "friend"}
        onClose={() => setActiveModal(null)}
        friends={friends.friends}
        onSearchUsers={friends.handleSearchUsers}
        onSendFriendRequest={friends.handleSendFriendRequest}
        friendRequests={friends.friendRequests}
        onAcceptRequest={friends.handleAcceptRequest}
        onRejectRequest={friends.handleRejectRequest}
        onLoadChatHistory={friends.getFriendChatHistory}
        onSendMessage={friends.sendFriendMessage}
      />

      <KBModal
        open={activeModal === "kb"}
        onClose={() => setActiveModal(null)}
      />

      <MemoryModal
        open={activeModal === "memory"}
        onClose={() => setActiveModal(null)}
      />
    </div>
  );
}
