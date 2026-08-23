package com.edushift.modules.ai.entity;

import com.edushift.modules.ai.entity.AiChatMessage.Role;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link Role} to the lowercase wire values enforced by
 * {@code chk_chat_messages_role} in V42 ({@code user}, {@code assistant},
 * {@code system}). JPA {@code EnumType.STRING} would persist {@code USER},
 * which violates the check constraint.
 */
@Converter(autoApply = false)
public class AiChatRoleConverter implements AttributeConverter<Role, String> {

    @Override
    public String convertToDatabaseColumn(Role role) {
        if (role == null) {
            return null;
        }
        return role.name().toLowerCase();
    }

    @Override
    public Role convertToEntityAttribute(String dbValue) {
        if (dbValue == null || dbValue.isBlank()) {
            return null;
        }
        return Role.valueOf(dbValue.trim().toUpperCase());
    }
}
