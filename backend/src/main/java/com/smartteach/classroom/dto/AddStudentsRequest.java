package com.smartteach.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Student names in display order, e.g. pasted from a class list, one name per line. */
public record AddStudentsRequest(
        @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 100) String> names) {
}
