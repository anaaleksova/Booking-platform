package com.bookingsaas.common.validation;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Checks {@link ValidPhone} with Google's libphonenumber, the same rules the booking form uses. */
public class PhoneValidator implements ConstraintValidator<ValidPhone, String> {

    private static final PhoneNumberUtil PHONES = PhoneNumberUtil.getInstance();

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("+")) {
            return false;   // without a country code the number is ambiguous
        }
        try {
            return PHONES.isValidNumber(PHONES.parse(trimmed, null));
        } catch (NumberParseException ex) {
            return false;
        }
    }
}
