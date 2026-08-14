package dev.matheus.payment.domain.model;

import dev.matheus.payment.domain.enums.UserType;
import java.util.Objects;

public final class User {

    private final UserId id;
    private final String fullName;
    private final Document document;
    private final Email email;
    private final String passwordHash;
    private final UserType type;

    private User(
            UserId id,
            String fullName,
            Document document,
            Email email,
            String passwordHash,
            UserType type
    ) {
        this.id = id;
        this.fullName = fullName;
        this.document = document;
        this.email = email;
        this.passwordHash = passwordHash;
        this.type = type;
    }

    public static User create(
            UserId id,
            String fullName,
            Document document,
            Email email,
            String passwordHash,
            UserType type
    ) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(document, "document is required");
        Objects.requireNonNull(email, "email is required");
        Objects.requireNonNull(type, "type is required");
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("full name is required");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("password hash is required");
        }
        return new User(id, fullName.trim(), document, email, passwordHash, type);
    }

    public boolean canSendMoney() {
        return type == UserType.COMMON;
    }

    public UserId id() {
        return id;
    }

    public String fullName() {
        return fullName;
    }

    public Document document() {
        return document;
    }

    public Email email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public UserType type() {
        return type;
    }
}
