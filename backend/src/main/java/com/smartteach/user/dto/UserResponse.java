package com.smartteach.user.dto;

import com.smartteach.user.entity.Role;

public record UserResponse(Long id, String email, String fullName, Role role) {
}
