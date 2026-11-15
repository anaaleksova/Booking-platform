package com.bookingsaas.event.share;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventSocialPostRepository extends JpaRepository<EventSocialPost, Long> {

    List<EventSocialPost> findByEventIdOrderByCreatedAtDesc(Long eventId);

    List<EventSocialPost> findByBusinessIdAndStatus(Long businessId, EventSocialPost.Status status);
}
