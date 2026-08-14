package dev.matheus.payment.domain.model;

import dev.matheus.payment.domain.enums.DocumentType;
import dev.matheus.payment.domain.exception.InvalidDocumentException;
import java.util.Objects;

public final class Document {

    private final DocumentType type;
    private final String value;

    private Document(DocumentType type, String value) {
        this.type = type;
        this.value = value;
    }

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

    public DocumentType type() {
        return type;
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Document document)) {
            return false;
        }
        return type == document.type && value.equals(document.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        return type + ":" + value;
    }
}
