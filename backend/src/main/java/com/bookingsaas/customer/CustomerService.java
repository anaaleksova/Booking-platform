package com.bookingsaas.customer;

import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.customer.dto.CustomerRequest;
import com.bookingsaas.customer.dto.CustomerResponse;
import com.bookingsaas.customer.spi.CustomerActivity;
import com.bookingsaas.customer.spi.CustomerActivityProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Customer records, always within one tenant. */
@Service
public class CustomerService {

    private final CustomerRepository repository;
    private final CustomerActivityProvider activityProvider;
    private final CurrentTenant currentTenant;

    public CustomerService(CustomerRepository repository,
                           CustomerActivityProvider activityProvider,
                           CurrentTenant currentTenant) {
        this.repository = repository;
        this.activityProvider = activityProvider;
        this.currentTenant = currentTenant;
    }

    // --- cross-module lookups ---------------------------------------------

    @Transactional(readOnly = true)
    public Customer requireForBusiness(Long businessId, Long customerId) {
        return repository.findByIdAndBusinessId(customerId, businessId)
                .orElseThrow(() -> ApiException.notFound("Customer", customerId));
    }

    @Transactional(readOnly = true)
    public long countFor(Long businessId) {
        return repository.countByBusinessId(businessId);
    }

    /**
     * Finds the existing customer behind a reservation, or creates one.
     *
     * <p>Matching on email first, then phone, keeps a regular from accumulating
     * a new row on every visit, while still working for customers who give
     * only one of the two.
     */
    @Transactional
    public Customer findOrCreate(Long businessId, String firstName, String lastName, String email, String phone) {
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
            // Fill in details we did not have before — unless another record
            // already holds them — but never overwrite what the business has
            // already curated.
            if (customer.getEmail() == null && cleanEmail != null
                    && repository.findByEmail(businessId, cleanEmail).isEmpty()) {
                customer.setEmail(cleanEmail);
            }
            if (customer.getPhone() == null && cleanPhone != null
                    && repository.findByBusinessIdAndPhone(businessId, cleanPhone).isEmpty()) {
                customer.setPhone(cleanPhone);
            }
            return repository.save(customer);
        }

        Customer customer = new Customer();
        customer.setBusinessId(businessId);
        customer.setFirstName(firstName.trim());
        customer.setLastName(lastName == null ? "" : lastName.trim());
        customer.setEmail(cleanEmail);
        customer.setPhone(cleanPhone);
        return repository.save(customer);
    }

    // --- admin API ---------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<CustomerResponse> search(String query, int page, int size) {
        Long businessId = currentTenant.businessId();
        String term = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        Page<Customer> customers = repository.search(businessId, term,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by("lastName").ascending().and(Sort.by("firstName").ascending())));

        // One aggregate query for the whole page, not one per row.
        Map<Long, CustomerActivity> activity = activityProvider.activityOf(businessId,
                customers.getContent().stream().map(Customer::getId).toList());
        return customers.map(customer -> CustomerResponse.from(customer, activity.get(customer.getId())));
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        Long businessId = currentTenant.businessId();
        return toResponse(businessId, requireForBusiness(businessId, id));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Long businessId = currentTenant.businessId();
        Customer customer = new Customer();
        customer.setBusinessId(businessId);
        apply(customer, request);
        return toResponse(businessId, save(customer));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Long businessId = currentTenant.businessId();
        Customer customer = requireForBusiness(businessId, id);
        apply(customer, request);
        return toResponse(businessId, save(customer));
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
                    "This customer has reservations and cannot be deleted.");
        }
    }

    private CustomerResponse toResponse(Long businessId, Customer customer) {
        return CustomerResponse.from(customer,
                activityProvider.activityOf(businessId, List.of(customer.getId())).get(customer.getId()));
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
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName() == null ? "" : request.lastName().trim());
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
