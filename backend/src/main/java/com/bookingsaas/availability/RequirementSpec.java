package com.bookingsaas.availability;

import java.util.List;

/**
 * One resource requirement of a service, with its candidate pool already
 * resolved.
 *
 * @param eligibleResourceIds active resources of the type that may serve this
 *                            service, before any time-based filtering
 * @param customerChoosable   whether a customer may pick the concrete
 *                            resources — the requirement's own flag combined
 *                            with the service's master switch
 * @param combinable          {@code quantity} is a minimum: when no single set
 *                            of that size seats the group, the engine combines
 *                            more resources of the type (tables pushed
 *                            together, desks side by side) — found per
 *                            booking, never configured
 */
public record RequirementSpec(
        Long requirementId,
        Long resourceTypeId,
        String resourceTypeName,
        int quantity,
        boolean required,
        boolean customerChoosable,
        boolean combinable,
        List<Long> eligibleResourceIds
) {

    /** A requirement for exactly {@code quantity} resources. */
    public RequirementSpec(Long requirementId, Long resourceTypeId, String resourceTypeName, int quantity,
                           boolean required, boolean customerChoosable, List<Long> eligibleResourceIds) {
        this(requirementId, resourceTypeId, resourceTypeName, quantity, required, customerChoosable, false,
                eligibleResourceIds);
    }
}
