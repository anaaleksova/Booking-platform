package com.bookingsaas.business;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GalleryImageRepository extends JpaRepository<GalleryImage, Long> {

    List<GalleryImage> findByBusinessIdOrderByPositionAscIdAsc(Long businessId);

    Optional<GalleryImage> findByIdAndBusinessId(Long id, Long businessId);

    long countByBusinessId(Long businessId);
}
