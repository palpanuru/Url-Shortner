package com.urlshortener.link;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    Optional<ShortLink> findByCode(String code);

    boolean existsByCode(String code);

    /** Single atomic UPDATE; safe under concurrency, unlike load-increment-save. */
    @Modifying
    @Query("update ShortLink l set l.clickCount = l.clickCount + 1 where l.id = :id")
    int incrementClickCount(@Param("id") long id);

    /** Bulk delete of long-expired links; click events go via ON DELETE CASCADE. */
    @Modifying
    @Query("delete from ShortLink l where l.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
