package com.aegis.backend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NoLinksValidator implements ConstraintValidator<NoLinks, String> {

    // Regex per individuare http, https, www, ftp
    private static final String URL_REGEX = ".*(?i)(http|https|ftp|www\\.).*";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true; // Lasciamo che @NotBlank gestisca i null
        }
        // Ritorna TRUE se NON c'è match con la regex (quindi è valido)
        return !value.matches(URL_REGEX);
    }
}