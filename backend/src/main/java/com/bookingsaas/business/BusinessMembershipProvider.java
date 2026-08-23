package com.bookingsaas.business;

import com.bookingsaas.common.security.BusinessMembership;
import com.bookingsaas.common.security.MembershipProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** The business module's answer to "which tenants may this user act for?". */
@Service
public class BusinessMembershipProvider implements MembershipProvider {

    private final BusinessUserRepository businessUserRepository;
    private final BusinessRepository businessRepository;

    public BusinessMembershipProvider(BusinessUserRepository businessUserRepository,
                                      BusinessRepository businessRepository) {
        this.businessUserRepository = businessUserRepository;
        this.businessRepository = businessRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BusinessMembership> membershipsOf(Long userId) {
        List<BusinessUser> links = businessUserRepository.findByUserId(userId);
        if (links.isEmpty()) {
            return List.of();
        }
        Map<Long, Business> businesses = businessRepository
                .findAllById(links.stream().map(BusinessUser::getBusinessId).toList())
                .stream()
                .collect(Collectors.toMap(Business::getId, Function.identity()));

        return links.stream()
                .map(link -> businesses.get(link.getBusinessId()))
                // A deactivated business grants nothing, even to its owner.
                .filter(business -> business != null && business.isActive())
                .map(business -> new BusinessMembership(business.getId(), business.getName(), business.getSlug(),
                        links.stream()
                                .filter(link -> link.getBusinessId().equals(business.getId()))
                                .findFirst()
                                .orElseThrow()
                                .getRole()))
                .toList();
    }
}
