package nmtest.common;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraintvalidation.SupportedValidationTarget;
import jakarta.validation.constraintvalidation.ValidationTarget;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Кросс-параметрическое ограничение: первый int-параметр не больше второго. */
@Constraint(validatedBy = OrderedRange.Validator.class)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OrderedRange {

  String message();

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  @SupportedValidationTarget(ValidationTarget.PARAMETERS)
  class Validator implements ConstraintValidator<OrderedRange, Object[]> {
    @Override
    public boolean isValid(Object[] values, ConstraintValidatorContext context) {
      return (int) values[0] <= (int) values[1];
    }
  }
}
