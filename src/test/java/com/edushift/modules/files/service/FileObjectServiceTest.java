package com.edushift.modules.files.service;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.files.repository.FileObjectRepository;
import com.edushift.modules.files.storage.StorageService;
import com.edushift.modules.files.validator.FileValidator;
import com.edushift.modules.files.config.StorageProperties;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
@ExtendWith(MockitoExtension.class)
class FileObjectServiceTest {
    @Mock StorageService storage; @Mock FileObjectRepository repo; @Mock FileValidator validator; @Mock StorageProperties props;
    @InjectMocks FileObjectService service;
    @Test void deleteNotFound() { when(repo.findByPublicUuid(any())).thenReturn(Optional.empty()); assertThatThrownBy(() -> service.delete(UUID.randomUUID())).isInstanceOf(com.edushift.modules.files.exception.FileNotFoundException.class); }
}
