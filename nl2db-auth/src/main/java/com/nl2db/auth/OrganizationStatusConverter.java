package com.nl2db.auth;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists organization status values using the lowercase representation
 * established by the database schema.
 */
@Converter(autoApply = true)
public class OrganizationStatusConverter implements AttributeConverter<OrganizationStatus, String> {

    @Override
    public String convertToDatabaseColumn(OrganizationStatus attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public OrganizationStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : OrganizationStatus.fromValue(dbData);
    }
}
