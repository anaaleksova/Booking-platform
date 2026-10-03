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
 */
public record RequirementSpec(
        Long requirementId,
        Long resourceTypeId,
        String resourceTypeName,
        int quantity,
        boolean required,
        boolean customerChoosable,
        List<Long> eligibleResourceIds
) {
}
