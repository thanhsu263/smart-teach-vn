package com.smartteach.user.service;

import com.smartteach.common.exception.ApiException;
import com.smartteach.user.dto.SettingsRequest;
import com.smartteach.user.dto.UserResponse;
import com.smartteach.user.entity.User;
import com.smartteach.user.mapper.UserMapper;
import com.smartteach.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserResponse getById(Long id) {
        return userMapper.toResponse(find(id));
    }

    @Transactional
    public UserResponse updateSettings(Long id, SettingsRequest request) {
        User user = find(id);
        user.setEmailNotifications(request.emailNotifications());
        return userMapper.toResponse(user);
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
