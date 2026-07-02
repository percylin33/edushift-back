package com.edushift.modules.materials.service.impl;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import com.edushift.modules.materials.repository.MaterialRepository;
import com.edushift.modules.materials.mapper.MaterialMapper;
import com.edushift.modules.materials.service.MaterialService;
import com.edushift.modules.materials.service.impl.MaterialServiceImpl;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.academic.section.repository.SectionRepository;
import com.edushift.shared.security.CurrentUserProvider;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
@ExtendWith(MockitoExtension.class)
class MaterialServiceImplTest {
    @Mock MaterialRepository repo; @Mock MaterialMapper mapper; @Mock FileObjectService fileObjectService;
    @Mock SectionRepository sectionRepo; @Mock CurrentUserProvider currentUser;
    @InjectMocks MaterialServiceImpl service;
    @Test void getByPublicUuidNotFound() { when(repo.findByPublicUuid(any())).thenReturn(Optional.empty()); assertThatThrownBy(() -> service.getByPublicUuid(UUID.randomUUID())).isInstanceOf(com.edushift.modules.materials.exception.MaterialNotFoundException.class); }
}
