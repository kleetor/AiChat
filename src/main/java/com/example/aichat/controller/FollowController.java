package com.example.aichat.controller;

import com.example.aichat.model.Follow;
import com.example.aichat.service.FollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/follows")
public class FollowController {

    @Autowired
    private FollowService followService;

    /** 关注用户 */
    @PostMapping("/{userId}")
    public ResponseEntity<?> follow(@PathVariable Long userId, Authentication auth) {
        Long followerId = (Long) auth.getPrincipal();
        try {
            followService.follow(followerId, userId);
            return ResponseEntity.ok(Map.of("following", true));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 取消关注 */
    @DeleteMapping("/{userId}")
    public ResponseEntity<?> unfollow(@PathVariable Long userId, Authentication auth) {
        Long followerId = (Long) auth.getPrincipal();
        followService.unfollow(followerId, userId);
        return ResponseEntity.ok(Map.of("following", false));
    }

    /** 检查关注状态 */
    @GetMapping("/{userId}/status")
    public ResponseEntity<Map<String, Boolean>> isFollowing(@PathVariable Long userId, Authentication auth) {
        Long followerId = (Long) auth.getPrincipal();
        return ResponseEntity.ok(Map.of("following", followService.isFollowing(followerId, userId)));
    }

    /** 我的关注 */
    @GetMapping("/following")
    public ResponseEntity<Page<Map<String, Object>>> getFollowing(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Page<Follow> follows = followService.getFollowing(userId, page, size);
        return ResponseEntity.ok(follows.map(this::toFollowedUserMap));
    }

    /** 我的粉丝 */
    @GetMapping("/followers")
    public ResponseEntity<Page<Map<String, Object>>> getFollowers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Page<Follow> follows = followService.getFollowers(userId, page, size);
        return ResponseEntity.ok(follows.map(this::toFollowerUserMap));
    }

    /** 关注/粉丝统计 */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("followingCount", followService.getFollowingCount(userId));
        stats.put("followersCount", followService.getFollowersCount(userId));
        return ResponseEntity.ok(stats);
    }

    private Map<String, Object> toFollowedUserMap(Follow f) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId", f.getFollowed().getId());
        map.put("username", f.getFollowed().getUsername());
        map.put("avatarUrl", f.getFollowed().getAvatarUrl());
        map.put("signature", f.getFollowed().getSignature());
        map.put("createdAt", f.getCreatedAt());
        return map;
    }

    private Map<String, Object> toFollowerUserMap(Follow f) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId", f.getFollower().getId());
        map.put("username", f.getFollower().getUsername());
        map.put("avatarUrl", f.getFollower().getAvatarUrl());
        map.put("signature", f.getFollower().getSignature());
        map.put("createdAt", f.getCreatedAt());
        return map;
    }
}
