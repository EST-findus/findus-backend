package com.findus.backend.auth.security;

import java.util.UUID;
import com.findus.backend.member.entity.MemberRole;

public record MemberPrincipal(UUID id, UUID sessionId, MemberRole role) {}
