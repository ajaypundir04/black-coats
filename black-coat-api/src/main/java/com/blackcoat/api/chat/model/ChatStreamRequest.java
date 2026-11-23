package com.blackcoat.api.chat.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ChatStreamRequest(
    UUID conversationId,

    @NotBlank(message = "Question cannot be blank")
    @Size(max = 2000, message = "Question cannot exceed 2000 characters")
    String question
) {}
