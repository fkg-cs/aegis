package com.aegis.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = NoLinksValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoLinks {
    String message() default "Input non valido: rilevati link o URL non consentiti";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}