package dev.matheus.payment.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.matheus.payment.domain.enums.DocumentType;
import dev.matheus.payment.domain.exception.InvalidDocumentException;
import dev.matheus.payment.domain.exception.InvalidEmailException;
import org.junit.jupiter.api.Test;

class DocumentAndEmailTest {

    @Test
    void normalizesDocumentDigits() {
        Document cpf = Document.of(DocumentType.CPF, "123.456.789-01");
        assertEquals("12345678901", cpf.value());
        assertEquals(DocumentType.CPF, cpf.type());
    }

    @Test
    void rejectsInvalidDocumentLength() {
        assertThrows(
                InvalidDocumentException.class,
                () -> Document.of(DocumentType.CPF, "123")
        );
        assertThrows(
                InvalidDocumentException.class,
                () -> Document.of(DocumentType.CNPJ, "12345678901")
        );
    }

    @Test
    void normalizesEmailToLowercase() {
        Email email = Email.of("User@Example.COM");
        assertEquals("user@example.com", email.value());
    }

    @Test
    void rejectsInvalidEmail() {
        assertThrows(InvalidEmailException.class, () -> Email.of("not-an-email"));
    }
}
