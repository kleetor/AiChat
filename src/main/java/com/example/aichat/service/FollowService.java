package com.example.aichat.service;

import com.example.aichat.config.BusinessException;
import com.example.aichat.model.Follow;
import com.example.aichat.model.User;
import com.example.aichat.repository.FollowRepository;
import com.example.aichat.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FollowService {

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public void follow(Long followerId, Long followedId) {
        if (followerId.equals(followedId)) {
            throw BusinessException.badRequest("不能关注自己");
        }
        if (!userRepository.existsById(followedId)) {
            throw BusinessException.notFound("用户不存在");
        }
        if (followRepository.existsByFollowerIdAndFollowedId(followerId, followedId)) {
            return; // 已关注，幂等
        }
        User follower = userRepository.getReferenceById(followerId);
        User followed = userRepository.getReferenceById(followedId);
        Follow follow = Follow.builder()
                .follower(follower)
                .followed(followed)
                .build();
        followRepository.save(follow);
    }

    @Transactional
    public void unfollow(Long followerId, Long followedId) {
        followRepository.deleteByFollowerIdAndFollowedId(followerId, followedId);
    }

    public boolean isFollowing(Long followerId, Long followedId) {
        return followRepository.existsByFollowerIdAndFollowedId(followerId, followedId);
    }

    public Page<Follow> getFollowing(Long userId, int page, int size) {
        return followRepository.findByFollowerIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
    }

    public Page<Follow> getFollowers(Long userId, int page, int size) {
        return followRepository.findByFollowedIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
    }

    public long getFollowingCount(Long userId) {
        return followRepository.countByFollowerId(userId);
    }

    public long getFollowersCount(Long userId) {
        return followRepository.countByFollowedId(userId);
    }
}
