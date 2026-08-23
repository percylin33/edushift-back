package com.edushift.modules.ai.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.ai.entity.AiChatMessage.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AiChatRoleConverterTest {

    private final AiChatRoleConverter converter = new AiChatRoleConverter();

    @Test
    @DisplayName("persists lowercase role values expected by chk_chat_messages_role")
    void toDatabaseColumn() {
        assertThat(converter.convertToDatabaseColumn(Role.USER)).isEqualTo("user");
        assertThat(converter.convertToDatabaseColumn(Role.ASSISTANT)).isEqualTo("assistant");
        assertThat(converter.convertToDatabaseColumn(Role.SYSTEM)).isEqualTo("system");
    }

    @Test
    @DisplayName("reads lowercase DB values back into Java enum")
    void toEntityAttribute() {
        assertThat(converter.convertToEntityAttribute("user")).isEqualTo(Role.USER);
        assertThat(converter.convertToEntityAttribute("assistant")).isEqualTo(Role.ASSISTANT);
        assertThat(converter.convertToEntityAttribute("system")).isEqualTo(Role.SYSTEM);
    }
}
