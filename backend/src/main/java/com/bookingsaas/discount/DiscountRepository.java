package com.bookingsaas.discount;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DiscountRepository extends JpaRepository<Discount, Long> {

    Optional<Discount> findByIdAndBusinessId(Long id, Long businessId);

    List<Discount> findByBusinessIdOrderByCreatedAtDesc(Long businessId);

    @Query("select d from Discount d where d.businessId = :businessId and upper(d.code) = upper(:code)")
    Optional<Discount> findByCode(@Param("businessId") Long businessId, @Param("code") String code);

    /**
     * Takes one use, but only while uses remain. The row lock taken by the
     * UPDATE serialises concurrent redemptions; the loser sees 0 rows.
     *
     * @return 1 when a use was taken, 0 when the limit is already reached
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
           update discount set usage_count = usage_count + 1, updated_at = now()
           where id = :id and (max_usage is null or usage_count < max_usage)
           """, nativeQuery = true)
    int redeem(@Param("id") Long id);

    /** Gives a use back when a reservation that took one is cancelled. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
           update discount set usage_count = usage_count - 1, updated_at = now()
           where id = :id and usage_count > 0
           """, nativeQuery = true)
    int release(@Param("id") Long id);
}
