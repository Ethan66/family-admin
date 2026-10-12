package com.ethan.familyadmin.auth.dto;

public record LoginResponse(String accessToken, CurrentUserResponse user) {
}

