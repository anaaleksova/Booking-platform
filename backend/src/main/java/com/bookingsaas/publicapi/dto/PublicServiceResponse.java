package com.bookingsaas.publicapi.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A service as shown to customers — no buffers, no internals.
 *
 * @param choosableRequirements what the customer picks before choosing a time
 *                              ("choose your therapist"), with every resource
 *                              that may serve this service; empty when the
 *                              business assigns everything
 */
public record PublicServiceResponse(
        Long id,
        String name,
        String description,
        String category,
        String imageUrl,
        int durationMinutes,
        BigDecimal price,
        boolean pricePerParticipant,
        int maxParticipants,
        String currency,
        List<ChoosableRequirement> choosableRequirements
) {

    /**
     * @param floorPlans maps of the place for this resource type, one per floor,
     *                   in order; empty when the business has not drawn any —
     *                   customers then pick from a list
     */
    public record ChoosableRequirement(Long requirementId, String resourceTypeName, int quantity,
                                       List<Option> resources, List<FloorPlan> floorPlans) {
    }

    /**
     * Positions and sizes are percentages of the plan; x and y are the centre.
     * {@code decor} is the rest of the room (areas, walls, bar, plants, entrance).
     */
    public record FloorPlan(Long id, String name, BigDecimal aspectRatio, List<Spot> spots, List<Decor> decor) {
    }

    public record Decor(String kind, String label, String tone, BigDecimal x, BigDecimal y, BigDecimal width,
                        BigDecimal height) {
    }

    public record Spot(Long resourceId, BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height,
                       String shape) {
    }

    /**
     * @param shared       other groups may be seated at it too, until its seats are taken
     * @param minPartySize smallest group it is given to (private resources only)
     */
    public record Option(Long id, String name, String description, String imageUrl, Integer capacity,
                         boolean shared, Integer minPartySize) {
    }
}
