package com.bookingsaas.integration.identity;

import com.bookingsaas.integration.ChannelProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerExternalIdentityRepository extends JpaRepository<CustomerExternalIdentity, Long> {

    Optional<CustomerExternalIdentity> findByBusinessIdAndProviderAndExternalUserId(Long businessId,
                                                                                    ChannelProvider provider,
                                                                                    String externalUserId);

    List<CustomerExternalIdentity> findByBusinessIdAndCustomerId(Long businessId, Long customerId);
}
