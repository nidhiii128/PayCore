package io.paycore.webhook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateWebhookRequest(@NotBlank @Size(max = 2048) String url, @NotEmpty List<String> eventTypes) {
}
