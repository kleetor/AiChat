import { useState, useCallback, useRef, useEffect } from "react";
import {
  getFriendList,
  getPendingFriendRequests,
  searchUsers,
  sendFriendRequest as apiSendFriendRequest,
  acceptFriendRequest,
  rejectFriendRequest,
  getFriendChatHistory,
  sendFriendMessage as apiSendFriendMessage,
  type FriendInfo,
  type FriendRequest,
} from "@/lib/services";

export function useFriends() {
  const [friends, setFriends] = useState<FriendInfo[]>([]);
  const [friendRequests, setFriendRequests] = useState<FriendRequest[]>([]);

  const loadFriends = useCallback(async () => {
    try {
      const [list, pending] = await Promise.all([
        getFriendList(),
        getPendingFriendRequests(),
      ]);
      setFriends(list);
      setFriendRequests(pending);
    } catch (e) {
      console.warn("加载好友列表失败:", e);
    }
  }, []);

  const handleSearchUsers = useCallback(async (query: string) => {
    try {
      return await searchUsers(query);
    } catch (e) {
      console.warn("搜索用户失败:", e);
      return [];
    }
  }, []);

  const handleSendFriendRequest = useCallback(async (userId: number) => {
    await apiSendFriendRequest(userId);
    await loadFriends();
  }, [loadFriends]);

  const handleAcceptRequest = useCallback(async (friendshipId: number) => {
    await acceptFriendRequest(friendshipId);
    await loadFriends();
  }, [loadFriends]);

  const handleRejectRequest = useCallback(async (friendshipId: number) => {
    await rejectFriendRequest(friendshipId);
    await loadFriends();
  }, [loadFriends]);

  return {
    friends,
    friendRequests,
    loadFriends,
    handleSearchUsers,
    handleSendFriendRequest,
    handleAcceptRequest,
    handleRejectRequest,
    getFriendChatHistory,
    sendFriendMessage: apiSendFriendMessage,
  };
}
