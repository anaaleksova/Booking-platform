package com.bookingsaas.integration.conversation;

import com.bookingsaas.common.domain.Auditable;
import com.bookingsaas.integration.ChannelProvider;
import com.bookingsaas.integration.conversation.ConversationEnums.Flow;
import com.bookingsaas.integration.conversation.ConversationEnums.State;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One chat with a social user, from the first message to a booked or
 * cancelled reservation. {@code data} holds the collected answers and the
 * options last offered, as JSON — see {@link ConversationData}.
 */
@Entity
@Table(name = "integration_conversation")
@Getter
@Setter
@NoArgsConstructor
public class Conversation extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChannelProvider provider;

    @Column(name = "external_user_id", nullable = false, length = 128)
    private String externalUserId;

    @Column(name = "customer_id")
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Flow flow;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private State state;

    @Column(nullable = false, columnDefinition = "text")
    private String data = "{}";

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "last_message", length = 2000)
    private String lastMessage;

    @Column(name = "last_execution_id", length = 128)
    private String lastExecutionId;

    @Column(nullable = false)
    private boolean active = true;

    /** Ends the conversation; a new message starts a fresh one. */
    public void close(State finalState) {
        this.state = finalState;
        this.active = false;
    }
}
