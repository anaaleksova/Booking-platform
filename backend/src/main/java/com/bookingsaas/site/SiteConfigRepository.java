package com.bookingsaas.site;

import org.springframework.data.jpa.repository.JpaRepository;

/** Keyed by business id: the row is the business's own, there is no other id to reach it by. */
public interface SiteConfigRepository extends JpaRepository<SiteConfig, Long> {
}
