package com.findus.backend.favorite.service;

import java.util.UUID;
import com.findus.backend.favorite.dto.FavoriteResponse;
import com.findus.backend.favorite.dto.FavoritePageResponse;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Pageable;

public interface MemberFavoriteService {
    FavoriteResponse register(@NotNull UUID memberId,@NotNull UUID missingPersonId);
    void cancel(@NotNull UUID memberId,@NotNull UUID missingPersonId);
    FavoritePageResponse findMine(@NotNull UUID memberId,@NotNull Pageable pageable);
}
