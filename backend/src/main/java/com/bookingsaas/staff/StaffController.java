package com.bookingsaas.staff;

import com.bookingsaas.auth.dto.AuthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Employees' login access: the owner's endpoints, and the invitation link's. */
@RestController
@Tag(name = "Staff access")
public class StaffController {

    private final StaffAccessService staffAccess;

    public StaffController(StaffAccessService staffAccess) {
        this.staffAccess = staffAccess;
    }

    // --- owner ---------------------------------------------------------------

    @GetMapping("/api/admin/staff")
    @Operation(summary = "Login access of every employee: none, invited, expired or active")
    public List<StaffAccessService.StaffAccess> list() {
        return staffAccess.list();
    }

    @PostMapping("/api/admin/employees/{employeeId}/invite")
    @Operation(summary = "Email the employee an invitation to log in (again: replaces the earlier link)")
    public StaffAccessService.StaffAccess invite(@PathVariable Long employeeId) {
        return staffAccess.invite(employeeId);
    }

    @DeleteMapping("/api/admin/employees/{employeeId}/invite")
    public ResponseEntity<Void> cancelInvitation(@PathVariable Long employeeId) {
        staffAccess.cancelInvitation(employeeId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/admin/employees/{employeeId}/access")
    @Operation(summary = "Take the employee's login access away")
    public ResponseEntity<Void> removeAccess(@PathVariable Long employeeId) {
        staffAccess.removeAccess(employeeId);
        return ResponseEntity.noContent().build();
    }

    // --- the invited person ----------------------------------------------------

    @GetMapping("/api/auth/invitations/{token}")
    public StaffAccessService.InvitationInfo describe(@PathVariable String token) {
        return staffAccess.describe(token);
    }

    public record NewAccount(@NotBlank @Size(max = 160) String fullName,
                             @NotBlank @Size(min = 8, max = 128, message = "must be at least 8 characters")
                             String password) {
    }

    @PostMapping("/api/auth/invitations/{token}/accept-new")
    @Operation(summary = "Accept by creating an account for the invited address")
    public AuthResponse acceptWithNewAccount(@PathVariable String token, @Valid @RequestBody NewAccount request) {
        return staffAccess.acceptWithNewAccount(token, request.fullName(), request.password());
    }

    @PostMapping("/api/auth/invitations/{token}/accept")
    @Operation(summary = "Accept with the account you are signed in with")
    public AuthResponse acceptAsSignedIn(@PathVariable String token) {
        return staffAccess.acceptAsSignedIn(token);
    }
}
