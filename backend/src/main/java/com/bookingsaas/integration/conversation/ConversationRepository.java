package com.bookingsaas.integration.conversation;

import com.bookingsaas.integration.ChannelProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByBusinessIdAndProviderAndExternalUserIdAndActiveTrue(Long businessId,
                                                                                   ChannelProvider provider,
                                                                                   String externalUserId);

    Optional<Conversation> findByIdAndBusinessId(Long id, Long businessId);

    /**
     * Holds one person's chat to a single writer until the transaction ends:
     * a PostgreSQL advisory lock on the person, so it also covers the moment
     * before their conversation exists.
     */
    @Query(value = "select cast(pg_advisory_xact_lock(hashtextextended(:key, 0)) as text)", nativeQuery = true)
    String lockPerson(@Param("key") String key);

    List<Conversation> findByBusinessIdAndReservationId(Long businessId, Long reservationId);

    /** Reservations booked through this social user's chats, linked or not. */
    @Query("""
           select c.reservationId from Conversation c
           where c.businessId = :businessId and c.provider = :provider
             and c.externalUserId = :externalUserId and c.reservationId is not null
           """)
    List<Long> reservationIdsBookedBy(@Param("businessId") Long businessId,
                                      @Param("provider") ChannelProvider provider,
                                      @Param("externalUserId") String externalUserId);
}
