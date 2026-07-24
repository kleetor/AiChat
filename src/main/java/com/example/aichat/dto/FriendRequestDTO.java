package com.example.aichat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class FriendRequestDTO {
    @NotNull
    private Long userId;
    private Long friendshipId;
    @Size(max = 500, message = "消息内容不能超过500字")
    private String content;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getFriendshipId() { return friendshipId; }
    public void setFriendshipId(Long friendshipId) { this.friendshipId = friendshipId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
