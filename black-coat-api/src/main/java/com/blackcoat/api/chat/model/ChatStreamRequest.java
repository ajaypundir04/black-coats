package com.blackcoat.api.chat.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ChatStreamRequest(
    UUID conversationId,

    @NotBlank(message = "Question cannot be blank")
    @Size(min = 3, message = "Question must be at least 3 characters")
    @Size(max = 2000, message = "Question cannot exceed 2000 characters")
    @Pattern(regexp = "^(?!.*[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]).*$", message = "Question contains illegal control characters")
    String question
) {}
