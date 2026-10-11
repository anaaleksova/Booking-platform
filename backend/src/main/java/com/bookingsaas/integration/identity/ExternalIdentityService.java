package com.bookingsaas.integration.identity;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.customer.Customer;
import com.bookingsaas.customer.CustomerService;
import com.bookingsaas.integration.ChannelProvider;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Maps social users to customers.
 *
 * <p>The rule that keeps this safe: an identity is only ever linked to a
 * customer record created for it. Contact details typed into a chat are not
 * verified, so a social user who gives the phone number of an existing
 * customer may book under that record — as anyone can on the website — but
 * is not linked to it, and therefore never sees or cancels that customer's
 * other reservations.
 */
@Service
public class ExternalIdentityService {

    private static final Logger log = LoggerFactory.getLogger(ExternalIdentityService.class);
    private static final PhoneNumberUtil PHONES = PhoneNumberUtil.getInstance();

    private final CustomerExternalIdentityRepository repository;
    private final CustomerService customerService;

    public ExternalIdentityService(CustomerExternalIdentityRepository repository, CustomerService customerService) {
        this.repository = repository;
        this.customerService = customerService;
    }

    /** Contact details collected in a chat. */
    public record ContactDetails(String firstName, String lastName, String phone, String email) {
    }

    /**
     * @param created whether the customer record was created for this identity
     * @param linked  whether the identity now points at this customer
     */
    public record ResolvedCustomer(Customer customer, boolean created, boolean linked) {
    }

    @Transactional(readOnly = true)
    public Optional<Customer> linkedCustomer(Long businessId, ChannelProvider provider, String externalUserId) {
        return repository.findByBusinessIdAndProviderAndExternalUserId(businessId, provider, externalUserId)
                .map(identity -> customerService.requireForBusiness(businessId, identity.getCustomerId()));
    }

    /**
     * The customer to book for: the linked one if the identity is known,
     * otherwise the record matching the details, otherwise a new record that
     * the identity is linked to.
     */
    @Transactional
    public ResolvedCustomer resolve(Long businessId, ChannelProvider provider, String externalUserId,
                                    String displayName, ContactDetails details) {
        Optional<Customer> linked = linkedCustomer(businessId, provider, externalUserId);
        if (linked.isPresent()) {
            return new ResolvedCustomer(linked.get(), false, true);
        }
        if (details == null || isBlank(details.firstName())) {
            throw ApiException.validation("The customer's first name and a phone number or email are required.");
        }
        String phone = normalizePhone(details.phone());
        String email = isBlank(details.email()) ? null : details.email().trim();
        if (phone == null && email == null) {
            throw ApiException.validation("A phone number or an email address is required.");
        }

        Optional<Customer> existing = customerService.findByContact(businessId, email, phone);
        if (existing.isPresent()) {
            log.info("Social user {}:{} matched existing customer {} by contact details; not linked",
                    provider, externalUserId, existing.get().getId());
            return new ResolvedCustomer(existing.get(), false, false);
        }

        Customer customer = customerService.findOrCreate(businessId, details.firstName(), details.lastName(),
                email, phone);
        CustomerExternalIdentity identity = new CustomerExternalIdentity();
        identity.setBusinessId(businessId);
        identity.setCustomerId(customer.getId());
        identity.setProvider(provider);
        identity.setExternalUserId(externalUserId);
        identity.setDisplayName(isBlank(displayName) ? null : displayName.trim());
        repository.save(identity);
        log.info("Social user {}:{} linked to new customer {}", provider, externalUserId, customer.getId());
        return new ResolvedCustomer(customer, true, true);
    }

    /**
     * International format, or a validation error. Chat users type numbers in
     * every style; storing one form keeps them matchable.
     */
    public static String normalizePhone(String raw) {
        return normalizePhone(raw, null);
    }

    /**
     * @param defaultRegion how to read a number written without a country code
     *                      ("072 345 678"), e.g. "MK"; null requires the code
     */
    public static String normalizePhone(String raw, String defaultRegion) {
        if (isBlank(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("00")) {
            trimmed = "+" + trimmed.substring(2);
        }
        try {
            var number = PHONES.parse(trimmed, defaultRegion);
            if (!PHONES.isValidNumber(number)) {
                throw ApiException.validation("The phone number is not valid.");
            }
            return PHONES.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException ex) {
            throw ApiException.validation("The phone number needs a country code, e.g. +389 70 123 456.");
        }
    }

    /**
     * The country a business's own phone number belongs to, e.g. "MK" for
     * +389 2 312 4567 — the natural reading of local numbers its customers type.
     * Null when the business has no internationally written number.
     */
    public static String regionOf(String businessPhone) {
        if (isBlank(businessPhone)) {
            return null;
        }
        try {
            String region = PHONES.getRegionCodeForNumber(PHONES.parse(businessPhone.trim(), null));
            return region == null || "ZZ".equals(region) ? null : region;
        } catch (NumberParseException ex) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
