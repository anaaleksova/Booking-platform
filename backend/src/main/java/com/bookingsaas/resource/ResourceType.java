package com.bookingsaas.resource;

import com.bookingsaas.common.domain.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A kind of reservable thing, defined by the business: "Treatment Room",
 * "Table", "Gaming Station". Adding one is data, never a code change.
 *
 * <p>One type per business is flagged as the employee type. Its resources are
 * the employees' representation in the availability system and are managed
 * through the employee module rather than directly.
 */
@Entity
@Table(name = "resource_type")
@Getter
@Setter
@NoArgsConstructor
public class ResourceType extends Auditable {

    public static final String DEFAULT_EMPLOYEE_TYPE_NAME = "Employee";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "is_employee_type", nullable = false)
    private boolean employeeType;

    @Column(nullable = false)
    private boolean active = true;
}
