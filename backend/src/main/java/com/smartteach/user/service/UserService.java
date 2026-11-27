package com.smartteach.user.service;

import com.smartteach.common.exception.ApiException;
import com.smartteach.user.dto.SettingsRequest;
import com.smartteach.user.dto.UserResponse;
import com.smartteach.user.entity.User;
import com.smartteach.user.mapper.UserMapper;
import com.smartteach.user.repository.UserRepository;
import java.util.Optional;
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

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    /** The email must already be normalised (trimmed, lower case), as it is stored that way. */
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /** Flushes at once, so a duplicate email fails here (unique index) and the caller can report it. */
    @Transactional
    public User save(User user) {
        return userRepository.saveAndFlush(user);
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
    }
}
