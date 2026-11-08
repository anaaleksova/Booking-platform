package com.bookingsaas.media;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaRepository extends JpaRepository<Media, UUID> {

    Optional<Media> findByIdAndBusinessId(UUID id, Long businessId);

    /** Ids only: loading the entities would read every image's bytes. */
    @Query("select m.id from Media m where m.businessId = :businessId and m.id in :ids")
    List<UUID> findOwnedIds(@Param("businessId") Long businessId, @Param("ids") Collection<UUID> ids);
}
