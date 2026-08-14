package dev.matheus.payment.adapter.out.persistence.mapper;

import dev.matheus.payment.adapter.out.persistence.entity.UserJpaEntity;
import dev.matheus.payment.domain.model.Document;
import dev.matheus.payment.domain.model.Email;
import dev.matheus.payment.domain.model.User;
import dev.matheus.payment.domain.model.UserId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserPersistenceMapper {

    default User toDomain(UserJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return User.create(
                UserId.of(entity.getId()),
                entity.getFullName(),
                Document.of(entity.getDocumentType(), entity.getDocumentValue()),
                Email.of(entity.getEmail()),
                entity.getPasswordHash(),
                entity.getType()
        );
    }

    @Mapping(target = "id", expression = "java(user.id().value())")
    @Mapping(target = "fullName", expression = "java(user.fullName())")
    @Mapping(target = "documentType", expression = "java(user.document().type())")
    @Mapping(target = "documentValue", expression = "java(user.document().value())")
    @Mapping(target = "email", expression = "java(user.email().value())")
    @Mapping(target = "passwordHash", expression = "java(user.passwordHash())")
    @Mapping(target = "type", expression = "java(user.type())")
    @Mapping(target = "createdAt", expression = "java(java.time.Instant.now())")
    UserJpaEntity toEntity(User user);
}
