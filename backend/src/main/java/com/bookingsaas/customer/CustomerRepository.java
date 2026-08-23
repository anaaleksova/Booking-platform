package com.bookingsaas.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByIdAndBusinessId(Long id, Long businessId);

    List<Customer> findByBusinessIdAndIdIn(Long businessId, Collection<Long> ids);

    @Query("""
           select c from Customer c
           where c.businessId = :businessId
             and (:term is null
                  or lower(c.name) like :term
                  or lower(coalesce(c.email, '')) like :term
                  or coalesce(c.phone, '') like :term)
           """)
    Page<Customer> search(@Param("businessId") Long businessId, @Param("term") String term, Pageable pageable);

    /* Matching on email or phone is what stops a repeat customer becoming a
       second row every time they book from the public page. */
    @Query("select c from Customer c where c.businessId = :businessId and lower(c.email) = lower(:email)")
    Optional<Customer> findByEmail(@Param("businessId") Long businessId, @Param("email") String email);

    Optional<Customer> findByBusinessIdAndPhone(Long businessId, String phone);
}
