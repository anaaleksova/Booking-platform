package com.bookingsaas.integration.conversation;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.customer.Customer;
import com.bookingsaas.integration.ChannelProvider;
import com.bookingsaas.integration.conversation.ConversationDtos.TurnRequest;
import com.bookingsaas.integration.conversation.ConversationEnums.Flow;
import com.bookingsaas.integration.conversation.ConversationEnums.State;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/** Loading, starting and saving conversations, and their JSON state. */
@Component
class ConversationStore {

    private static final Logger log = LoggerFactory.getLogger(ConversationStore.class);
    /** A chat untouched for this long starts over: yesterday's half-booking is stale. */
    static final Duration CONVERSATION_TTL = Duration.ofHours(24);

    private final ConversationRepository repository;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final Clock clock;

    ConversationStore(ConversationRepository repository, ObjectMapper objectMapper, EntityManager entityManager,
                      Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    /**
     * Waits until no other turn for this person is running, then holds them
     * until the transaction commits. Two messages sent close together are
     * therefore handled one after the other, the second seeing what the first
     * left, instead of both starting from the same state and one overwriting
     * the other.
     */
    void lockPerson(Long businessId, ChannelProvider provider, String externalUserId) {
        repository.lockPerson("conversation:" + businessId + ":" + provider + ":" + externalUserId);
    }

    /** The conversation, read again once its person is locked: what a concurrent turn wrote counts. */
    Conversation requireLocked(Long businessId, Long conversationId) {
        Conversation conversation = require(businessId, conversationId);
        lockPerson(businessId, conversation.getProvider(), conversation.getExternalUserId());
        entityManager.refresh(conversation);
        return conversation;
    }

    /**
     * A resend: the same words as the last message, sent before that one was
     * answered (impatient customers repeat themselves when a reply is slow).
     * The same words sent after the answer are a new message — "yes" can
     * answer two questions in a row.
     */
    static boolean isRepeat(Conversation conversation, TurnRequest request) {
        if (request.sentAt() == null || request.message() == null || conversation.getLastMessage() == null
                || conversation.getUpdatedAt() == null) {
            return false;
        }
        return normalized(request.message()).equals(normalized(conversation.getLastMessage()))
                && request.sentAt().isBefore(conversation.getUpdatedAt());
    }

    private static String normalized(String message) {
        return message.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /** The person's open conversation, unless it has gone stale (then it is closed). */
    Optional<Conversation> active(Long businessId, ChannelProvider provider, String externalUserId) {
        Optional<Conversation> found = repository.findByBusinessIdAndProviderAndExternalUserIdAndActiveTrue(
                businessId, provider, externalUserId);
        if (found.isPresent() && found.get().getUpdatedAt() != null
                && found.get().getUpdatedAt().plus(CONVERSATION_TTL).isBefore(clock.instant())) {
            Conversation stale = found.get();
            stale.close(State.CANCELLED);
            repository.saveAndFlush(stale);
            log.info("Conversation {} expired", stale.getId());
            return Optional.empty();
        }
        return found;
    }

    /** A new conversation in {@code flow}; the one it replaces, if any, is closed first. */
    Conversation start(ConversationTurn turn, Conversation previous, Flow flow, TurnRequest request) {
        if (previous != null) {
            previous.close(State.CANCELLED);
            repository.saveAndFlush(previous);
        }
        Conversation conversation = new Conversation();
        conversation.setBusinessId(turn.businessId());
        conversation.setProvider(turn.provider());
        conversation.setExternalUserId(turn.externalUserId());
        conversation.setCustomerId(turn.linked().map(Customer::getId).orElse(null));
        conversation.setFlow(flow);
        conversation.setState(flow == Flow.BOOKING ? State.WAITING_FOR_SERVICE : State.WAITING_FOR_CANCEL_SELECTION);
        ConversationData data = new ConversationData();
        data.setDisplayName(request.displayName());
        conversation.setData(write(data));
        Conversation saved = repository.saveAndFlush(conversation);
        log.info("Conversation {} started: flow={} provider={} externalUser={}", saved.getId(), flow,
                turn.provider(), turn.externalUserId());
        return saved;
    }

    /** Records the message and workflow execution behind this turn. */
    static void touch(Conversation conversation, TurnRequest request) {
        if (request.message() != null) {
            conversation.setLastMessage(request.message().length() > 2000
                    ? request.message().substring(0, 2000) : request.message());
        }
        if (request.executionId() != null) {
            conversation.setLastExecutionId(request.executionId());
        }
    }

    Conversation require(Long businessId, Long conversationId) {
        return repository.findByIdAndBusinessId(conversationId, businessId)
                .orElseThrow(() -> ApiException.notFound("Conversation", conversationId));
    }

    ConversationData read(Conversation conversation) {
        try {
            return objectMapper.readValue(conversation.getData(), ConversationData.class);
        } catch (JsonProcessingException ex) {
            log.warn("Unreadable state on conversation {}; starting it over", conversation.getId());
            return new ConversationData();
        }
    }

    void save(Conversation conversation, ConversationData data) {
        conversation.setData(write(data));
        repository.save(conversation);
    }

    void save(Conversation conversation) {
        repository.save(conversation);
    }

    private String write(ConversationData data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not store conversation state", ex);
        }
    }
}
