package com.smartteach.user.mapper;

import com.smartteach.user.dto.UserResponse;
import com.smartteach.user.entity.User;
import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {

    UserResponse toResponse(User user);
}
