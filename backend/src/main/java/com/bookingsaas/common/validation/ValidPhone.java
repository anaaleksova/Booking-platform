package com.bookingsaas.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A phone number in international form ({@code +} and country code) that is a
 * real, dialable number for that country: right length and number ranges, not
 * just digits. Null is valid; combine with {@code @NotBlank} to require one.
 */
@Documented
@Constraint(validatedBy = PhoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPhone {

    String message() default "is not a valid phone number for the selected country";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
