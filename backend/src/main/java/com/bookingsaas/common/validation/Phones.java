package com.bookingsaas.common.validation;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

/**
 * One form for every phone number: E.164, "+38970555444".
 *
 * <p>A customer is recognised by phone in every channel (website, admin,
 * Instagram, Facebook), which only works if "+389 70 555 444",
 * "070 555 444" and "+38970555444" are stored as the same string. A local
 * number is read in the business's own country.
 */
public final class Phones {

    private static final PhoneNumberUtil PHONES = PhoneNumberUtil.getInstance();

    private Phones() {
    }

    /**
     * The country a business's own phone number belongs to, e.g. "MK" for
     * +389 2 312 4567 — the natural reading of local numbers its customers
     * type. Null when the business has no internationally written number.
     */
    public static String regionOf(String businessPhone) {
        if (businessPhone == null || businessPhone.isBlank()) {
            return null;
        }
        try {
            String region = PHONES.getRegionCodeForNumber(PHONES.parse(businessPhone.trim(), null));
            return region == null || "ZZ".equals(region) ? null : region;
        } catch (NumberParseException ex) {
            return null;
        }
    }

    /**
     * The number in E.164, or as typed (trimmed) when it cannot be read — a
     * local number with no country to read it in, say. Null for blank.
     *
     * @param defaultRegion the country local numbers belong to, e.g. "MK"; may be null
     */
    public static String normalize(String raw, String defaultRegion) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            return PHONES.format(PHONES.parse(trimmed, defaultRegion), PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException ex) {
            return trimmed;
        }
    }
}
