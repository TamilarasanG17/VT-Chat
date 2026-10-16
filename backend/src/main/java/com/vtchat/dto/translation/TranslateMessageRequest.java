package com.vtchat.dto.translation;

import jakarta.validation.constraints.Size;

public record TranslateMessageRequest(@Size(max = 16) String targetLanguage) {
}
