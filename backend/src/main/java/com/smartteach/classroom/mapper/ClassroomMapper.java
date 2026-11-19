package com.smartteach.classroom.mapper;

import com.smartteach.classroom.dto.ClassResponse;
import com.smartteach.classroom.dto.StudentCodeResponse;
import com.smartteach.classroom.dto.StudentResponse;
import com.smartteach.classroom.entity.Classroom;
import com.smartteach.classroom.entity.Student;
import org.mapstruct.Mapper;

@Mapper
public interface ClassroomMapper {

    ClassResponse toResponse(Classroom classroom, long studentCount);

    StudentResponse toResponse(Student student);

    StudentCodeResponse toCodeResponse(Student student, String accessCode);
}
