package com.edushift.modules.admin.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminLoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 1, max = 128, message = "Password length out of range")
        String password
) {

    @Override
    public String toString() {
        return "AdminLoginRequest[email=" + email + ", password=***]";
    }
}
