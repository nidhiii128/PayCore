package io.paycore.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(@NotBlank @Size(max = 200) String name, @NotNull AccountType type) {
}
