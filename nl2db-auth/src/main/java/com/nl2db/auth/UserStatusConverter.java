package com.nl2db.auth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists user status values using the lowercase representation established
 * by the database schema.
 */
@Converter(autoApply = true)
public class UserStatusConverter implements AttributeConverter<UserStatus, String> {

    @Override
    public String convertToDatabaseColumn(UserStatus attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public UserStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : UserStatus.fromValue(dbData);
    }
}
