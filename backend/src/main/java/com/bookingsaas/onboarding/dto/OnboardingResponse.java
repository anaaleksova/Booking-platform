package com.bookingsaas.onboarding.dto;

import com.bookingsaas.business.BusinessType;

import java.util.List;

/**
 * The "Getting started" checklist.
 *
 * @param businessType what the owner signed up as; decides the steps' wording
 * @param slug         for the booking link the last step shares
 * @param steps        in the order they are shown
 * @param dismissed    the owner hid the checklist
 */
public record OnboardingResponse(BusinessType businessType, String slug, List<Step> steps, boolean dismissed) {

    public enum StepKey {
        SERVICES,
        RESOURCES,
        HOURS,
        /** Ticked by the business having an address and a phone, not by the owner. */
        CONTACT,
        /** Ticked by the business having a logo or a cover photo, not by the owner. */
        PHOTO,
        SHARE;

        /** Whether the owner can tick it ("Looks good", "Copy link"); the rest follow the business's data. */
        public boolean markable() {
            return this != CONTACT && this != PHOTO;
        }
    }

    public record Step(StepKey key, boolean done) {
    }
}
