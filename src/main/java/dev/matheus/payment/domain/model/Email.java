package dev.matheus.payment.domain.model;

import dev.matheus.payment.domain.exception.InvalidEmailException;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
@EqualsAndHashCode
@ToString
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Email {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private final String value;

    public static Email of(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new InvalidEmailException("email is required");
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new InvalidEmailException("invalid email format");
        }
        return new Email(normalized);
    }
}
