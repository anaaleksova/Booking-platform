package com.bookingsaas.staff;

import com.bookingsaas.auth.AuthService;
import com.bookingsaas.auth.dto.AuthResponse;
import com.bookingsaas.business.Business;
import com.bookingsaas.business.BusinessService;
import com.bookingsaas.common.config.AppProperties;
import com.bookingsaas.common.error.ApiException;
import com.bookingsaas.common.error.ErrorCode;
import com.bookingsaas.common.security.AuthenticatedUser;
import com.bookingsaas.common.security.CurrentTenant;
import com.bookingsaas.employee.Employee;
import com.bookingsaas.employee.EmployeeDeletedEvent;
import com.bookingsaas.employee.EmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Employees who can log in.
 *
 * <p>The owner invites an employee by email. The link in the email proves the
 * address, so accepting it either creates an account (name + password) or, if
 * the address already has one, asks the person to sign in first. Either way
 * they become STAFF of the business, linked to that employee — which is what
 * scopes "their" reservations. The owner can withdraw an invitation or take
 * the access away again at any time.
 */
@Service
public class StaffAccessService {

    private static final Logger log = LoggerFactory.getLogger(StaffAccessService.class);
    static final Duration INVITATION_LIFETIME = Duration.ofDays(7);

    private final StaffInvitationRepository invitations;
    private final StaffInviteSender sender;
    private final EmployeeService employeeService;
    private final BusinessService businessService;
    private final AuthService authService;
    private final CurrentTenant currentTenant;
    private final AppProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public StaffAccessService(StaffInvitationRepository invitations,
                              StaffInviteSender sender,
                              EmployeeService employeeService,
                              BusinessService businessService,
                              AuthService authService,
                              CurrentTenant currentTenant,
                              AppProperties properties,
                              Clock clock) {
        this.invitations = invitations;
        this.sender = sender;
        this.employeeService = employeeService;
        this.businessService = businessService;
        this.authService = authService;
        this.currentTenant = currentTenant;
        this.properties = properties;
        this.clock = clock;
    }

    // --- the owner's side ----------------------------------------------------

    public enum State {
        /** No login. */
        NONE,
        /** Invited, not accepted yet. */
        INVITED,
        /** Invited, but the link ran out: invite again. */
        EXPIRED,
        /** Can log in. */
        ACTIVE
    }

    /** One employee's login access, for the Employees page. */
    public record StaffAccess(Long employeeId, State state, String email, Instant invitedAt, Instant expiresAt) {
    }

    /** The signed-in member's own employee record: whose calendar "my time off" is. */
    public record OwnCalendar(Long employeeId, Long resourceId, String name) {
    }

    @Transactional(readOnly = true)
    public OwnCalendar ownCalendar() {
        return employeeService.employeeOf(currentTenant.businessId(), currentTenant.userId())
                .map(employee -> new OwnCalendar(employee.getId(), employee.getResourceId(), employee.getName()))
                .orElseThrow(() -> ApiException.notFound("You are not linked to an employee of this business."));
    }

    @Transactional(readOnly = true)
    public List<StaffAccess> list() {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        Map<Long, StaffInvitation> open = invitations.findByBusinessId(businessId).stream()
                .collect(Collectors.toMap(StaffInvitation::getEmployeeId, Function.identity()));
        Instant now = clock.instant();
        return employeeService.employeesOf(businessId).stream().map(employee -> {
            if (employee.getUserId() != null) {
                return new StaffAccess(employee.getId(), State.ACTIVE, employee.getEmail(), null, null);
            }
            StaffInvitation invitation = open.get(employee.getId());
            if (invitation == null) {
                return new StaffAccess(employee.getId(), State.NONE, employee.getEmail(), null, null);
            }
            return new StaffAccess(employee.getId(), invitation.getExpiresAt().isAfter(now) ? State.INVITED : State.EXPIRED,
                    invitation.getEmail(), invitation.getCreatedAt(), invitation.getExpiresAt());
        }).toList();
    }

    /**
     * Emails the employee an invitation to log in. Inviting again replaces the
     * earlier link, so only the newest one works.
     */
    @Transactional
    public StaffAccess invite(Long employeeId) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        Employee employee = employeeService.requireForBusiness(businessId, employeeId);
        if (employee.getUserId() != null) {
            throw ApiException.of(ErrorCode.CONFLICT, employee.getName() + " can already log in.");
        }
        if (employee.getEmail() == null || employee.getEmail().isBlank()) {
            throw ApiException.validation("Add " + employee.getName() + "'s email address first.");
        }
        String email = employee.getEmail().trim().toLowerCase(Locale.ROOT);
        if (email.equalsIgnoreCase(currentTenant.user().email())) {
            throw ApiException.validation("That is your own email address. Use the employee's own address.");
        }
        Business business = businessService.requireById(businessId);

        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        Instant now = clock.instant();
        StaffInvitation invitation = invitations.findByEmployeeId(employeeId).orElseGet(StaffInvitation::new);
        invitation.setBusinessId(businessId);
        invitation.setEmployeeId(employeeId);
        invitation.setEmail(email);
        invitation.setTokenHash(sha256(token));
        invitation.setCreatedAt(now);
        invitation.setExpiresAt(now.plus(INVITATION_LIFETIME));
        invitation.setInvitedByUserId(currentTenant.userId());
        invitations.save(invitation);

        try {
            sender.send(email, employee.getName(), business.getName(), properties.frontendUrl() + "/invite/" + token);
        } catch (RuntimeException ex) {
            log.warn("Could not send staff invitation for employee id={}: {}", employeeId, ex.getMessage());
            throw ApiException.of(ErrorCode.EMAIL_DELIVERY_FAILED,
                    "The invitation could not be emailed. Check the address and try again.");
        }
        log.info("Employee id={} of business id={} invited to log in", employeeId, businessId);
        return new StaffAccess(employeeId, State.INVITED, email, now, invitation.getExpiresAt());
    }

    /** Withdraws an open invitation: its link stops working. */
    @Transactional
    public void cancelInvitation(Long employeeId) {
        currentTenant.requireOwner();
        employeeService.requireForBusiness(currentTenant.businessId(), employeeId);
        invitations.deleteByEmployeeId(employeeId);
    }

    /** Takes the employee's login access away; their next request is refused. */
    @Transactional
    public void removeAccess(Long employeeId) {
        currentTenant.requireOwner();
        Long businessId = currentTenant.businessId();
        Employee employee = employeeService.requireForBusiness(businessId, employeeId);
        if (employee.getUserId() != null) {
            businessService.removeStaff(businessId, employee.getUserId());
            employeeService.unlinkUser(businessId, employeeId);
        }
        invitations.deleteByEmployeeId(employeeId);
        log.info("Login access removed for employee id={} of business id={}", employeeId, businessId);
    }

    /** A deleted employee's login goes with them. */
    @EventListener
    public void onEmployeeDeleted(EmployeeDeletedEvent event) {
        if (event.userId() != null) {
            businessService.removeStaff(event.businessId(), event.userId());
        }
    }

    // --- the invited person's side --------------------------------------------

    /**
     * What the invitation page shows before anything is decided.
     *
     * @param accountExists the address already has an account: sign in to accept
     */
    public record InvitationInfo(String businessName, String employeeName, String email, boolean accountExists) {
    }

    @Transactional(readOnly = true)
    public InvitationInfo describe(String token) {
        StaffInvitation invitation = require(token);
        Employee employee = employeeService.requireForBusiness(invitation.getBusinessId(), invitation.getEmployeeId());
        return new InvitationInfo(businessService.requireById(invitation.getBusinessId()).getName(), employee.getName(),
                invitation.getEmail(), authService.accountExists(invitation.getEmail()));
    }

    /** Accepting with a new account: the link has proven the email, so no code is needed. */
    @Transactional
    public AuthResponse acceptWithNewAccount(String token, String fullName, String password) {
        StaffInvitation invitation = require(token);
        Long userId = authService.createVerifiedAccount(invitation.getEmail(), fullName, password);
        return join(invitation, userId);
    }

    /** Accepting with the account the person is signed in with, which must be the invited address. */
    @Transactional
    public AuthResponse acceptAsSignedIn(String token) {
        StaffInvitation invitation = require(token);
        AuthenticatedUser user = currentTenant.user();
        if (!user.email().equalsIgnoreCase(invitation.getEmail())) {
            throw ApiException.accessDenied("This invitation is for " + invitation.getEmail()
                    + ". Sign in with that address to accept it.");
        }
        return join(invitation, user.userId());
    }

    private AuthResponse join(StaffInvitation invitation, Long userId) {
        Long businessId = invitation.getBusinessId();
        Employee employee = employeeService.requireForBusiness(businessId, invitation.getEmployeeId());
        if (employee.getUserId() != null) {
            throw ApiException.of(ErrorCode.CONFLICT, "This invitation has already been used.");
        }
        businessService.addStaff(businessId, userId);
        employeeService.linkUser(businessId, employee.getId(), userId);
        invitations.delete(invitation);
        log.info("Employee id={} of business id={} accepted the invitation (user id={})", employee.getId(),
                businessId, userId);
        return authService.sessionFor(userId, businessId);
    }

    private StaffInvitation require(String token) {
        return invitations.findByTokenHash(sha256(token == null ? "" : token))
                .filter(invitation -> invitation.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> ApiException.of(ErrorCode.VERIFICATION_EXPIRED,
                        "This invitation has expired or was withdrawn. Ask for a new one."));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
