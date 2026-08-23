package com.bookingsaas.customer;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.customer.dto.CustomerRequest;
import com.bookingsaas.customer.dto.CustomerResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Customer records, always within one tenant. */
@Service
public class CustomerService {

    private final CustomerRepository repository;
    private final CurrentTenant currentTenant;

    public CustomerService(CustomerRepository repository, CurrentTenant currentTenant) {
        this.repository = repository;
        this.currentTenant = currentTenant;
    }

    // --- cross-module lookups ---------------------------------------------

    @Transactional(readOnly = true)
    public Customer requireForBusiness(Long businessId, Long customerId) {
        return repository.findByIdAndBusinessId(customerId, businessId)
                .orElseThrow(() -> ApiException.notFound("Customer", customerId));
    }

    /**
     * Finds the existing customer behind a booking, or creates one.
     *
     * <p>Matching on email first, then phone, keeps a regular from accumulating
     * a new row on every visit, while still working for the many customers who
     * give only one of the two.
     */
    @Transactional
    public Customer findOrCreate(Long businessId, String name, String phone, String email, String notes) {
        String cleanEmail = normalize(email);
        String cleanPhone = normalize(phone);
        if (cleanEmail == null && cleanPhone == null) {
            throw ApiException.validation("A phone number or an email address is required.");
        }

        Optional<Customer> existing = Optional.<Customer>empty()
                .or(() -> cleanEmail == null ? Optional.empty() : repository.findByEmail(businessId, cleanEmail))
                .or(() -> cleanPhone == null ? Optional.empty()
                        : repository.findByBusinessIdAndPhone(businessId, cleanPhone));

        if (existing.isPresent()) {
            Customer customer = existing.get();
            // Fill in details we did not have before, but never overwrite what
            // the business has already curated.
            if (customer.getEmail() == null && cleanEmail != null) {
                customer.setEmail(cleanEmail);
            }
            if (customer.getPhone() == null && cleanPhone != null) {
                customer.setPhone(cleanPhone);
            }
            return repository.save(customer);
        }

        Customer customer = new Customer();
        customer.setBusinessId(businessId);
        customer.setName(name.trim());
        customer.setEmail(cleanEmail);
        customer.setPhone(cleanPhone);
        customer.setNotes(normalize(notes));
        return repository.save(customer);
    }

    // --- dashboard API -----------------------------------------------------

    @Transactional(readOnly = true)
    public Page<CustomerResponse> search(String query, int page, int size) {
        String term = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        return repository.search(currentTenant.businessId(), term,
                        PageRequest.of(page, Math.min(size, 100), Sort.by("name").ascending()))
                .map(CustomerResponse::from);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return CustomerResponse.from(requireForBusiness(currentTenant.businessId(), id));
    }

    @Transactional(readOnly = true)
    public List<Customer> byIds(Long businessId, List<Long> ids) {
        return ids.isEmpty() ? List.of() : repository.findByBusinessIdAndIdIn(businessId, ids);
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Customer customer = new Customer();
        customer.setBusinessId(currentTenant.businessId());
        apply(customer, request);
        return CustomerResponse.from(save(customer));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = requireForBusiness(currentTenant.businessId(), id);
        apply(customer, request);
        return CustomerResponse.from(save(customer));
    }

    @Transactional
    public void delete(Long id) {
        currentTenant.requireOwner();
        Customer customer = requireForBusiness(currentTenant.businessId(), id);
        try {
            repository.delete(customer);
            repository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.of(ErrorCode.CUSTOMER_IN_USE,
                    "This customer has bookings and cannot be deleted.");
        }
    }

    private Customer save(Customer customer) {
        try {
            Customer saved = repository.save(customer);
            repository.flush();
            return saved;
        } catch (DataIntegrityViolationException ex) {
            // Unique indexes on (business_id, email) and (business_id, phone).
            throw ApiException.of(ErrorCode.DUPLICATE_CUSTOMER,
                    "Another customer already uses this email or phone number.");
        }
    }

    private static void apply(Customer customer, CustomerRequest request) {
        String email = normalize(request.email());
        String phone = normalize(request.phone());
        if (email == null && phone == null) {
            throw ApiException.validation("A phone number or an email address is required.");
        }
        customer.setName(request.name().trim());
        customer.setEmail(email);
        customer.setPhone(phone);
        customer.setNotes(normalize(request.notes()));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
