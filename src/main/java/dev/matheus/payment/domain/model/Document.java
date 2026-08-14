package dev.matheus.payment.domain.model;

import dev.matheus.payment.domain.enums.DocumentType;
import dev.matheus.payment.domain.exception.InvalidDocumentException;
import java.util.Objects;
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
public final class Document {

    private final DocumentType type;
    private final String value;

    public static Document of(DocumentType type, String rawValue) {
        if (type == null) {
            throw new InvalidDocumentException("document type is required");
        }
        if (rawValue == null || rawValue.isBlank()) {
            throw new InvalidDocumentException("document value is required");
        }

        String digits = rawValue.replaceAll("\\D", "");
        int expected = type == DocumentType.CPF ? 11 : 14;
        if (digits.length() != expected) {
            throw new InvalidDocumentException(
                    type + " must have exactly " + expected + " digits"
            );
        }
        return new Document(type, digits);
    }
}
