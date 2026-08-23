package com.bookingsaas.publicapi.dto;

import com.bookingsaas.staff.Staff;

/** A staff member as a customer chooses them: a name and a face, nothing else. */
public record PublicStaffResponse(Long id, String name, String photoUrl) {

    public static PublicStaffResponse from(Staff staff) {
        return new PublicStaffResponse(staff.getId(), staff.getName(), staff.getPhotoUrl());
    }
}
