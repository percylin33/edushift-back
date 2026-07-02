package com.edushift.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaseEntityTest {

    private static class TestEntity extends BaseEntity {
    }

    @Test
    @DisplayName("id is null before persist")
    void idIsNullByDefault() {
        var entity = new TestEntity();
        assertThat(entity.getId()).isNull();
    }

    @Test
    @DisplayName("createdAt and updatedAt are null before persist")
    void timestampsAreNullByDefault() {
        var entity = new TestEntity();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("deleted flag defaults to false")
    void deletedDefaultsToFalse() {
        var entity = new TestEntity();
        assertThat(entity.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("markDeleted() sets deleted to true")
    void markDeletedSetsDeletedToTrue() {
        var entity = new TestEntity();
        entity.markDeleted();
        assertThat(entity.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("restore() sets deleted to false after markDeleted")
    void restoreSetsDeletedToFalse() {
        var entity = new TestEntity();
        entity.markDeleted();
        entity.restore();
        assertThat(entity.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("equals returns true for same reference")
    void equalsSameReference() {
        var entity = new TestEntity();
        assertThat(entity).isEqualTo(entity);
    }

    @Test
    @DisplayName("equals returns false for null")
    void equalsNull() {
        var entity = new TestEntity();
        assertThat(entity).isNotEqualTo(null);
    }

    @Test
    @DisplayName("equals returns false for different class")
    void equalsDifferentClass() {
        var entity = new TestEntity();
        assertThat(entity).isNotEqualTo("some string");
    }

    @Test
    @DisplayName("equals returns true when both entities have same id")
    void equalsSameId() throws Exception {
        var id = UUID.randomUUID();
        var entity1 = new TestEntity();
        setId(entity1, id);
        var entity2 = new TestEntity();
        setId(entity2, id);

        assertThat(entity1).isEqualTo(entity2);
    }

    @Test
    @DisplayName("equals returns false when ids differ")
    void equalsDifferentId() throws Exception {
        var entity1 = new TestEntity();
        setId(entity1, UUID.randomUUID());
        var entity2 = new TestEntity();
        setId(entity2, UUID.randomUUID());

        assertThat(entity1).isNotEqualTo(entity2);
    }

    @Test
    @DisplayName("equals returns false when id is null on either entity")
    void equalsNullId() throws Exception {
        var entity1 = new TestEntity();
        var entity2 = new TestEntity();
        setId(entity2, UUID.randomUUID());

        assertThat(entity1).isNotEqualTo(entity2);
    }

    @Test
    @DisplayName("hashCode returns same value for same class regardless of id")
    void hashCodeSameClass() {
        var entity1 = new TestEntity();
        var entity2 = new TestEntity();
        assertThat(entity1.hashCode()).isEqualTo(entity2.hashCode());
    }

    @Test
    @DisplayName("hashCode differs between different classes")
    void hashCodeDifferentClass() {
        var entity1 = new TestEntity();

        var entity2 = new TestEntity() {
        };

        assertThat(entity1.hashCode()).isNotEqualTo(entity2.hashCode());
    }

    @Test
    @DisplayName("toString includes id")
    void toStringIncludesId() throws Exception {
        var entity = new TestEntity();
        setId(entity, UUID.fromString("00000000-0000-0000-0000-000000000001"));
        assertThat(entity.toString()).contains("00000000-0000-0000-0000-000000000001");
    }

    private static void setId(BaseEntity entity, UUID id) throws Exception {
        var field = BaseEntity.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(entity, id);
    }
}
