package com.bookingsaas.integration.conversation;

import com.bookingsaas.business.Business;
import com.bookingsaas.customer.Customer;
import com.bookingsaas.integration.ChannelProvider;
import com.bookingsaas.integration.conversation.ConversationDtos.Problem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Everything that is fixed for the duration of one turn, plus the problems
 * found along the way, which the reply reports.
 */
record ConversationTurn(Business business, ChannelProvider provider, String externalUserId,
                        Optional<Customer> linked, List<Problem> problems) {

    ConversationTurn(Business business, ChannelProvider provider, String externalUserId, Optional<Customer> linked) {
        this(business, provider, externalUserId, linked, new ArrayList<>());
    }

    Long businessId() {
        return business.getId();
    }

    void problem(String code, String message) {
        problems.add(new Problem(code, message));
    }
}
