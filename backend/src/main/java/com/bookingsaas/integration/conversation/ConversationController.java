package com.bookingsaas.integration.conversation;

import com.bookingsaas.integration.ChannelProvider;
import com.bookingsaas.integration.auth.IntegrationAccess;
import com.bookingsaas.integration.auth.IntegrationPermission;
import com.bookingsaas.integration.conversation.ConversationDtos.ActionFailedRequest;
import com.bookingsaas.integration.conversation.ConversationDtos.ConversationContext;
import com.bookingsaas.integration.conversation.ConversationDtos.TurnRequest;
import com.bookingsaas.integration.conversation.ConversationDtos.TurnResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chat conversations with customers. The workflow asks for the context,
 * lets the language model interpret the message against it, submits the
 * interpretation as a turn and phrases the reply from the answer.
 */
@RestController
@RequestMapping("/api/integrations/businesses/{businessId}/conversations")
@Tag(name = "Integrations · Conversations")
public class ConversationController {

    private final IntegrationAccess access;
    private final ConversationService conversations;

    public ConversationController(IntegrationAccess access, ConversationService conversations) {
        this.access = access;
        this.conversations = conversations;
    }

    @GetMapping("/current")
    @Operation(summary = "Where the chat with this person stands, to interpret their next message")
    public ConversationContext current(@PathVariable Long businessId,
                                       @RequestParam ChannelProvider provider,
                                       @RequestParam String externalUserId) {
        access.require(businessId, IntegrationPermission.MANAGE_CONVERSATIONS);
        return conversations.context(businessId, provider, externalUserId);
    }

    @PostMapping("/turn")
    @Operation(summary = "Apply one interpreted message and get the facts for the reply")
    public TurnResponse turn(@PathVariable Long businessId, @Valid @RequestBody TurnRequest request) {
        access.require(businessId, IntegrationPermission.MANAGE_CONVERSATIONS);
        return conversations.turn(businessId, request);
    }

    @PostMapping("/{conversationId}/action-failed")
    @Operation(summary = "Report that the reservation or cancellation the conversation asked for was refused")
    public TurnResponse actionFailed(@PathVariable Long businessId, @PathVariable Long conversationId,
                                     @Valid @RequestBody ActionFailedRequest request) {
        access.require(businessId, IntegrationPermission.MANAGE_CONVERSATIONS);
        return conversations.actionFailed(businessId, conversationId, request);
    }
}
