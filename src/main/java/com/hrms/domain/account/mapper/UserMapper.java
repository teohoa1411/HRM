package com.hrms.domain.account.mapper;

import com.hrms.domain.account.dto.UserResponse;
import com.hrms.domain.account.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "active", source = "active")
    UserResponse toResponse(User user);
}
