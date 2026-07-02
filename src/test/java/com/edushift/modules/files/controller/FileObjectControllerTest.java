package com.edushift.modules.files.controller;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.files.entity.FileObject;
import com.edushift.modules.files.service.FileObjectService;
import com.edushift.modules.files.storage.StorageService;
import com.edushift.modules.files.config.StorageProperties;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.security.LmsRoleAuthorityMapper;
import com.edushift.shared.multitenancy.TenantResolver;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
@WebMvcTest(FileObjectController.class)
class FileObjectControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean FileObjectService fileObjectService; @MockitoBean StorageService storageService;
    @MockitoBean StorageProperties storageProperties; @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean TenantResolver tenantResolver;
    @MockitoBean JwtService jwtService; @MockitoBean LmsRoleAuthorityMapper roleAuthorityMapper;
    private static JwtAuthenticationToken admin() { return new JwtAuthenticationToken(new JwtAuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a", "a@t"), "t", List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))); }
    @Test void testGet() throws Exception { given(fileObjectService.findByPublicUuid(any())).willReturn(Optional.of(new FileObject())); mockMvc.perform(get("/v1/files/{id}", UUID.randomUUID()).with(authentication(admin()))).andExpect(status().isOk()); }
    @Test void testDelete() throws Exception { mockMvc.perform(delete("/v1/files/{id}", UUID.randomUUID()).with(csrf()).with(authentication(admin()))).andExpect(status().isOk()); }
    @Test void testUpload() throws Exception { var mf = new MockMultipartFile("file", "a.txt", "text/plain", "data".getBytes()); mockMvc.perform(multipart("/v1/files").file(mf).param("module", "materials").with(csrf()).with(authentication(admin()))).andExpect(status().isCreated()); }
    @Test void testDownload() throws Exception { given(fileObjectService.findByPublicUuid(any())).willReturn(Optional.of(new FileObject())); mockMvc.perform(get("/v1/files/{id}/download", UUID.randomUUID()).with(authentication(admin()))).andExpect(status().isOk()); }
    @Test void testCreateUploadRequest() throws Exception { mockMvc.perform(post("/v1/files/upload-requests").with(csrf()).with(authentication(admin())).content("""
        {"module":"materials","originalName":"a.txt","contentType":"text/plain","sizeBytes":100}
        """).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()); }
    @Test void testConfirmUpload() throws Exception { mockMvc.perform(post("/v1/files/{id}/confirm", UUID.randomUUID()).with(csrf()).with(authentication(admin())).content("""
        {"sizeBytes":100,"checksumSha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
        """).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()); }
}
