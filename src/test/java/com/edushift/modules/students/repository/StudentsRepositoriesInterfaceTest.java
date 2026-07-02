package com.edushift.modules.students.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.edushift.modules.students.entity.DocumentType;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudentsRepositoriesInterfaceTest {

    @Test
    @DisplayName("StudentRepository: findByPublicUuid / findByDocument / findByEmailIgnoreCase / findByUserId")
    void studentRepo() throws Exception {
        Class<?> repo = StudentRepository.class;
        assertThat(repo.getMethod("findByPublicUuid", UUID.class).getReturnType())
                .isAssignableFrom(Optional.class);
        assertThat(repo.getMethod("findByDocumentTypeAndDocumentNumber",
                DocumentType.class, String.class).getReturnType())
                .isAssignableFrom(Optional.class);
        assertThat(repo.getMethod("findByEmailIgnoreCase", String.class).getReturnType())
                .isAssignableFrom(Optional.class);
        assertThat(repo.getMethod("findByUserId", UUID.class).getReturnType())
                .isAssignableFrom(Optional.class);
    }

    @Test
    @DisplayName("GuardianRepository: findByPublicUuid / findByDocument / findByEmailIgnoreCase")
    void guardianRepo() throws Exception {
        Class<?> repo = GuardianRepository.class;
        assertThat(repo.getMethod("findByPublicUuid", UUID.class).getReturnType())
                .isAssignableFrom(Optional.class);
        assertThat(repo.getMethod("findByDocumentTypeAndDocumentNumber",
                DocumentType.class, String.class).getReturnType())
                .isAssignableFrom(Optional.class);
        assertThat(repo.getMethod("findByEmailIgnoreCase", String.class).getReturnType())
                .isAssignableFrom(Optional.class);
    }

    @Test
    @DisplayName("StudentGuardianRepository: findByPublicUuid / findActiveByStudentId / findActivePair / countActivePrimaryContacts")
    void studentGuardianRepo() throws Exception {
        Class<?> repo = StudentGuardianRepository.class;
        assertThat(repo.getMethod("findByPublicUuid", UUID.class).getReturnType())
                .isAssignableFrom(Optional.class);
        assertThat(repo.getMethod("findActiveByStudentId", UUID.class).getReturnType())
                .isAssignableFrom(java.util.List.class);
        assertThat(repo.getMethod("findActivePair", UUID.class, UUID.class).getReturnType())
                .isAssignableFrom(Optional.class);
        assertThat(repo.getMethod("countActivePrimaryContacts", UUID.class).getReturnType())
                .isEqualTo(long.class);
    }

    @Test
    @DisplayName("BulkImportJobRepository: findByPublicUuid")
    void bulkImportJobRepo() throws Exception {
        Class<?> repo = BulkImportJobRepository.class;
        assertThat(repo.getMethod("findByPublicUuid", UUID.class).getReturnType())
                .isAssignableFrom(Optional.class);
    }
}