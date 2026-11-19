package com.smartteach.assignment.mapper;

import com.smartteach.assignment.dto.AssignmentResponse;
import com.smartteach.assignment.entity.Assignment;
import org.mapstruct.Mapper;

@Mapper
public interface AssignmentMapper {

    AssignmentResponse toResponse(Assignment assignment, String className, Integer questionCount);
}
