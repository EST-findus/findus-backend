package com.findus.backend.missing.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.findus.backend.missing.entity.MissingPerson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MissingPersonRepository {
    List<MissingPerson> findAllForSync();
    Optional<MissingPerson> findForFavorite(UUID id);
    Optional<MissingPerson> findForImageUpdate(UUID id);
    void saveAll(List<MissingPerson> persons);
    boolean tryLock();
    Page<MissingPerson> findAvailable(String occurredOnOrBefore,Pageable page);
    Optional<MissingPerson> findAvailable(UUID id);
    Page<MissingPerson> findForMainReview(long minImages,Pageable page);
}
