package com.edushift.modules.materials.controller;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.edushift.modules.auth.security.JwtAuthenticatedPrincipal;
import com.edushift.modules.auth.security.JwtAuthenticationToken;
import com.edushift.modules.auth.service.JwtService;
import com.edushift.modules.materials.service.MaterialService;
import com.edushift.shared.security.CurrentUserProvider;
import com.edushift.shared.security.LmsRoleAuthorityMapper;
import com.edushift.shared.multitenancy.TenantResolver;
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
@WebMvcTest(MaterialController.class)
class MaterialControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean MaterialService materialService; @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean TenantResolver tenantResolver;
    @MockitoBean JwtService jwtService; @MockitoBean LmsRoleAuthorityMapper roleAuthorityMapper;
    private static JwtAuthenticationToken write() { return new JwtAuthenticationToken(new JwtAuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a", "a@t"), "t", List.of(new SimpleGrantedAuthority("LMS_MATERIAL_WRITE"))); }
    private static JwtAuthenticationToken read() { return new JwtAuthenticationToken(new JwtAuthenticatedPrincipal(UUID.randomUUID(), UUID.randomUUID(), "a", "a@t"), "t", List.of(new SimpleGrantedAuthority("LMS_MATERIAL_READ"))); }
    @Test void testCreateUpload() throws Exception { var mf = new MockMultipartFile("file", "a.txt", "text/plain", "data".getBytes()); var meta = new MockMultipartFile("metadata", "", "application/json", "{\"title\":\"T\"}".getBytes()); mockMvc.perform(multipart("/sections/{sid}/materials", UUID.randomUUID()).file(mf).file(meta).with(csrf()).with(authentication(write()))).andExpect(status().isCreated()); }
    @Test void testList() throws Exception { mockMvc.perform(get("/sections/{sid}/materials", UUID.randomUUID()).with(authentication(read()))).andExpect(status().isOk()); }
    @Test void testGet() throws Exception { mockMvc.perform(get("/materials/{id}", UUID.randomUUID()).with(authentication(read()))).andExpect(status().isOk()); }
    @Test void testPatch() throws Exception { mockMvc.perform(patch("/materials/{id}", UUID.randomUUID()).with(csrf()).with(authentication(write())).content("{\"title\":\"T\"}").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()); }
    @Test void testDelete() throws Exception { mockMvc.perform(delete("/materials/{id}", UUID.randomUUID()).with(csrf()).with(authentication(write()))).andExpect(status().isNoContent()); }
}
