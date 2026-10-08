package com.findus.backend.auth.service;

import com.findus.backend.auth.dto.IssuedTokens;
import com.findus.backend.auth.dto.LoginRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public interface AuthService {
	IssuedTokens login(@NotNull @Valid LoginRequest request);
	IssuedTokens refresh(String refreshToken);
	void logout(String refreshToken);
}
