package com.bookingsaas.onboarding;

import com.bookingsaas.business.BusinessCreatedEvent;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.employee.EmployeeService;
import com.bookingsaas.employee.dto.EmployeeRequest;
import com.bookingsaas.resource.ResourceService;
import com.bookingsaas.resource.ResourceTypeService;
import com.bookingsaas.resource.dto.ResourceRequest;
import com.bookingsaas.resource.dto.ResourceTypeRequest;
import com.bookingsaas.service.ServiceCatalogService;
import com.bookingsaas.service.dto.ServiceRequest;
import com.bookingsaas.service.dto.ServiceRequest.RequirementRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * The starting setup of a new business, by the type its owner picked at
 * sign-up: the resource types, a starter set of resources (tables, rooms,
 * the owner as the first employee) and services, so the booking page takes
 * bookings the moment sign-up finishes.
 *
 * <p>Runs inside the sign-up transaction and goes through the same services
 * as the admin, so a template is validated like anything an owner enters;
 * if it fails, the whole sign-up rolls back. The opening week and slot grid
 * come from {@link com.bookingsaas.business.BusinessType} itself.
 *
 * <p>The employee type keeps its neutral name: "Stylist" or "Therapist" fits
 * some salons and spas but not others, and the owner can rename it.
 */
@Component
public class BusinessTemplates {

    private static final Logger log = LoggerFactory.getLogger(BusinessTemplates.class);

    private final BusinessService businessService;
    private final ResourceTypeService typeService;
    private final ResourceService resourceService;
    private final EmployeeService employeeService;
    private final ServiceCatalogService serviceCatalog;
    private final BusinessOnboardingRepository onboardingRepository;
    private final Clock clock;

    public BusinessTemplates(BusinessService businessService,
                             ResourceTypeService typeService,
                             ResourceService resourceService,
                             EmployeeService employeeService,
                             ServiceCatalogService serviceCatalog,
                             BusinessOnboardingRepository onboardingRepository,
                             Clock clock) {
        this.businessService = businessService;
        this.typeService = typeService;
        this.resourceService = resourceService;
        this.employeeService = employeeService;
        this.serviceCatalog = serviceCatalog;
        this.onboardingRepository = onboardingRepository;
        this.clock = clock;
    }

    @EventListener
    public void onBusinessCreated(BusinessCreatedEvent event) {
        Builder builder = new Builder(event.businessId(),
                businessService.requireById(event.businessId()).getCurrency(), event.ownerName());
        switch (event.type()) {
            case RESTAURANT -> builder.restaurant();
            case HAIR_SALON -> builder.hairSalon();
            case NAILS_BEAUTY -> builder.nailsAndBeauty();
            case SPA -> builder.spa();
            case FITNESS -> builder.fitness();
            case COWORKING -> builder.coworking();
            case OTHER -> {
                // Nothing to pre-fill: the owner builds the setup themselves.
            }
        }
        onboardingRepository.save(new BusinessOnboarding(event.businessId(), clock.instant()));
        log.info("Business id={} set up from the {} template", event.businessId(), event.type());
    }

    /** Builds one business's setup; prices in euros, converted to its currency. */
    private final class Builder {

        private final Long businessId;
        private final String currency;
        private final String ownerName;

        Builder(Long businessId, String currency, String ownerName) {
            this.businessId = businessId;
            this.currency = currency;
            this.ownerName = ownerName == null || ownerName.isBlank() ? "Owner" : ownerName.trim();
        }

        /** Table booking only: one free reservation, so the booking page skips choosing a service. */
        void restaurant() {
            Long table = type("Table", "Tables customers can reserve. Draw a floor plan to let them pick one.");
            int[] seats = {2, 2, 4, 4, 4, 6};
            for (int i = 0; i < seats.length; i++) {
                resource(table, "Table " + (i + 1), seats[i], false);
            }
            service("Table reservation", "Reserve a table. Tell us in the notes if it is a special occasion.",
                    120, 0, false, 12, 0, true,
                    List.of(requirement(table, true, true)));
        }

        void hairSalon() {
            Long employee = owner();
            service("Haircut", null, 30, 15, false, 1, 0, true, List.of(requirement(employee, true, false)));
            service("Haircut & beard", null, 45, 20, false, 1, 0, true, List.of(requirement(employee, true, false)));
            service("Colouring", null, 90, 45, false, 1, 0, true, List.of(requirement(employee, true, false)));
        }

        void nailsAndBeauty() {
            Long employee = owner();
            service("Manicure", null, 45, 20, false, 1, 0, true, List.of(requirement(employee, true, false)));
            service("Pedicure", null, 60, 25, false, 1, 0, true, List.of(requirement(employee, true, false)));
            service("Gel nails", null, 75, 30, false, 1, 0, true, List.of(requirement(employee, true, false)));
        }

        void spa() {
            Long employee = owner();
            Long room = type("Treatment room", "Rooms a treatment takes place in.");
            resource(room, "Room 1", null, false);
            for (Object[] treatment : new Object[][] {
                    {"Classic massage", 60, 40}, {"Deep tissue massage", 60, 50}, {"Facial", 45, 35}}) {
                service((String) treatment[0], null, (int) treatment[1], (int) treatment[2], false, 1, 10, true,
                        List.of(requirement(employee, true, false), requirement(room, false, false)));
            }
        }

        /**
         * Classes without a timetable yet: any hour within opening hours can be
         * booked, and everyone who books the same hour shares the studio's places.
         * No trainer is required, since an employee is held by one booking at a time.
         */
        void fitness() {
            Long studio = type("Studio", "Where classes take place. Its capacity is the places in a class.");
            resource(studio, "Main studio", 12, true);
            service("Group class",
                    "A 60-minute class. Everyone who books the same hour joins the same class.",
                    60, 10, true, 4, 0, false, List.of(requirement(studio, false, false)));
        }

        void coworking() {
            Long desk = type("Desk", "Desks to book by the half or full day.");
            for (int i = 1; i <= 8; i++) {
                resource(desk, "Desk " + i, 1, false);
            }
            Long meetingRoom = type("Meeting room", "Rooms to book by the hour.");
            resource(meetingRoom, "Meeting room", 8, false);
            service("Desk, half day", "Four hours at a desk. Teams get desks next to each other.",
                    240, 10, false, 8, 0, true, List.of(requirement(desk, true, true)));
            service("Desk, full day", "Ten hours at a desk. Teams get desks next to each other.",
                    600, 18, false, 8, 0, true, List.of(requirement(desk, true, true)));
            service("Meeting room, 1 hour", null, 60, 15, false, 8, 0, false,
                    List.of(requirement(meetingRoom, false, false)));
        }

        // --- building blocks ---------------------------------------------------

        /** The owner, by their sign-up name, as the first employee; returns the employee type. */
        private Long owner() {
            employeeService.createFor(businessId,
                    new EmployeeRequest(ownerName, null, null, null, true, 1, List.of()));
            return typeService.ensureEmployeeType(businessId).getId();
        }

        private Long type(String name, String description) {
            return typeService.createFor(businessId, new ResourceTypeRequest(name, description, true)).id();
        }

        private void resource(Long typeId, String name, Integer capacity, boolean shared) {
            resourceService.createFor(businessId,
                    new ResourceRequest(typeId, name, null, capacity, true, shared, null, null, List.of()));
        }

        private void service(String name, String description, int minutes, int euros, boolean perPerson,
                             int maxParticipants, int bufferAfter, boolean customerChooses,
                             List<RequirementRequest> requirements) {
            serviceCatalog.createFor(businessId, new ServiceRequest(name, description, null, null, minutes,
                    SamplePrices.of(euros, currency), perPerson, maxParticipants, 0, bufferAfter,
                    customerChooses, true, requirements));
        }

        private static RequirementRequest requirement(Long typeId, boolean customerCanChoose, boolean combine) {
            return new RequirementRequest(typeId, 1, true, customerCanChoose, combine);
        }
    }
}
