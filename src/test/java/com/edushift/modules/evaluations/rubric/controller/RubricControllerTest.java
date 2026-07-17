package com.edushift.modules.evaluations.rubric.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edushift.modules.evaluations.rubric.dto.CriterionInput;
import com.edushift.modules.evaluations.rubric.dto.CreateRubricRequest;
import com.edushift.modules.evaluations.rubric.dto.DescriptorInput;
import com.edushift.modules.evaluations.rubric.dto.LevelInput;
import com.edushift.modules.evaluations.rubric.dto.RubricListItem;
import com.edushift.modules.evaluations.rubric.dto.RubricResponse;
import com.edushift.modules.evaluations.rubric.dto.UpdateRubricRequest;
import com.edushift.modules.evaluations.rubric.service.RubricService;
import com.edushift.shared.exception.ConflictException;
import com.edushift.shared.exception.ForbiddenException;
import com.edushift.shared.exception.GlobalExceptionHandler;
import com.edushift.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RubricController.class)
@Import({GlobalExceptionHandler.class, com.edushift.test.EdushiftWebMvcTestConfig.class})

class RubricControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private RubricService service;

    private RubricResponse stubResponse(UUID publicUuid, boolean isSystem) {
        return new RubricResponse(
                publicUuid, "My Rubric", "Desc",
                List.of(), List.of(), isSystem, null, Boolean.TRUE,
                Instant.now(), Instant.now());
    }

    private RubricListItem stubListItem(UUID publicUuid, String name, boolean isSystem) {
        return new RubricListItem(
                publicUuid, name, null,
                isSystem, null, 0, List.of(), Boolean.TRUE,
                Instant.now(), Instant.now());
    }

    private CreateRubricRequest validCreateRequest() {
        return new CreateRubricRequest(
                "My Rubric", "Desc",
                List.of(
                        new CriterionInput("a", "a", null,
                                new BigDecimal("50.00"),
                                List.of(new DescriptorInput("A", "OK"))),
                        new CriterionInput("b", "b", null,
                                new BigDecimal("50.00"),
                                List.of(new DescriptorInput("A", "OK")))),
                List.of(
                        new LevelInput("A", "A", 1),
                        new LevelInput("B", "B", 2)));
    }

    // =========================================================================
    // GET /academic/rubrics
    // =========================================================================

    @Nested
    @DisplayName("GET /academic/rubrics")
    class ListRubrics {

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("happy path — 200 with array")
        void happyPath() throws Exception {
            given(service.listRubrics(any())).willReturn(List.of(
                    stubListItem(UUID.randomUUID(), "My Rubric", false)));

            mockMvc.perform(get("/v1/academic/rubrics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("My Rubric"))
                    .andExpect(jsonPath("$[0].isSystem").value(false));
        }

        @Test
        @WithMockUser(roles = "TEACHER")
        @DisplayName("TEACHER role allowed")
        void teacherAllowed() throws Exception {
            given(service.listRubrics(any())).willReturn(List.of());

            mockMvc.perform(get("/v1/academic/rubrics"))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "STUDENT")
        @DisplayName("STUDENT role forbidden")
        void studentForbidden() throws Exception {
            mockMvc.perform(get("/v1/academic/rubrics"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("anonymous → 401")
        void anonymous() throws Exception {
            mockMvc.perform(get("/v1/academic/rubrics"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("systemOnly + q passthrough")
        void filtersPassthrough() throws Exception {
            given(service.listRubrics(any())).willReturn(List.of());

            mockMvc.perform(get("/v1/academic/rubrics?systemOnly=true&q=math"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /academic/rubrics/system")
    class ListSystem {

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("happy path — 200 with system seed")
        void happyPath() throws Exception {
            given(service.listSystemRubrics()).willReturn(List.of(
                    stubListItem(UUID.randomUUID(), "Ensayo argumentativo", true)));

            mockMvc.perform(get("/v1/academic/rubrics/system"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].isSystem").value(true));
        }
    }

    @Nested
    @DisplayName("GET /academic/rubrics/{publicUuid}")
    class GetRubric {

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("happy path")
        void happyPath() throws Exception {
            UUID id = UUID.randomUUID();
            given(service.getRubric(id)).willReturn(stubResponse(id, false));

            mockMvc.perform(get("/v1/academic/rubrics/{u}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.publicUuid").value(id.toString()));
        }

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("unknown → 404")
        void unknown() throws Exception {
            UUID id = UUID.randomUUID();
            given(service.getRubric(id))
                    .willThrow(new ResourceNotFoundException("Rubric", id));

            mockMvc.perform(get("/v1/academic/rubrics/{u}", id))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /academic/rubrics")
    class CreateRubric {

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("happy path — 201")
        void happyPath() throws Exception {
            UUID id = UUID.randomUUID();
            given(service.createRubric(any())).willReturn(stubResponse(id, false));

            mockMvc.perform(post("/v1/academic/rubrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.publicUuid").value(id.toString()));
        }

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("invalid shape — 400 (validation)")
        void invalidShape() throws Exception {
            var bad = new CreateRubricRequest(
                    "X", null,
                    List.of(new CriterionInput("a", "a", null,
                            new BigDecimal("99.00"),
                            List.of(new DescriptorInput("A", "OK")))),
                    List.of(new LevelInput("A", "A", 1),
                            new LevelInput("B", "B", 2)));

            mockMvc.perform(post("/v1/academic/rubrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(bad)))
                    .andExpect(status().isBadRequest());

            then(service).should(never()).createRubric(any());
        }

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("name collision → 409 RUB_NAME_EXISTS")
        void nameCollision() throws Exception {
            given(service.createRubric(any()))
                    .willThrow(new ConflictException("RUB_NAME_EXISTS", "dup"));

            mockMvc.perform(post("/v1/academic/rubrics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validCreateRequest())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errors[0].code").value("RUB_NAME_EXISTS"));
        }
    }

    @Nested
    @DisplayName("POST /academic/rubrics/{u}/fork")
    class ForkRubric {

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("happy path — 201, body is optional")
        void happyPath() throws Exception {
            UUID source = UUID.randomUUID();
            UUID fork = UUID.randomUUID();
            given(service.forkRubric(eq(source), any())).willReturn(stubResponse(fork, false));

            mockMvc.perform(post("/v1/academic/rubrics/{u}/fork", source)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isCreated());
        }

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("non-system source → 400 RUB_CANNOT_FORK_NON_SYSTEM")
        void nonSystem() throws Exception {
            UUID source = UUID.randomUUID();
            given(service.forkRubric(eq(source), any()))
                    .willThrow(new ConflictException("RUB_CANNOT_FORK_NON_SYSTEM", "no fork"));

            mockMvc.perform(post("/v1/academic/rubrics/{u}/fork", source)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PATCH /academic/rubrics/{u}")
    class UpdateRubric {

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("happy path — 200")
        void happyPath() throws Exception {
            UUID id = UUID.randomUUID();
            given(service.updateRubric(eq(id), any())).willReturn(stubResponse(id, false));

            var patch = new UpdateRubricRequest("Renamed", null, null, null);
            mockMvc.perform(patch("/v1/academic/rubrics/{u}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patch)))
                    .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("system rubric → 403 RUB_SYSTEM_READ_ONLY")
        void systemReadOnly() throws Exception {
            UUID id = UUID.randomUUID();
            given(service.updateRubric(eq(id), any()))
                    .willThrow(new ForbiddenException("RUB_SYSTEM_READ_ONLY", "fork instead"));

            var patch = new UpdateRubricRequest("Renamed", null, null, null);
            mockMvc.perform(patch("/v1/academic/rubrics/{u}", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patch)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errors[0].code").value("RUB_SYSTEM_READ_ONLY"));
        }
    }

    @Nested
    @DisplayName("DELETE /academic/rubrics/{u}")
    class DeleteRubric {

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("happy path — 204")
        void happyPath() throws Exception {
            UUID id = UUID.randomUUID();
            willDoNothing().given(service).deleteRubric(id);

            mockMvc.perform(delete("/v1/academic/rubrics/{u}", id))
                    .andExpect(status().isNoContent());

            then(service).should(times(1)).deleteRubric(id);
        }

        @Test
        @WithMockUser(roles = "TENANT_ADMIN")
        @DisplayName("system rubric → 403")
        void systemReadOnly() throws Exception {
            UUID id = UUID.randomUUID();
            willThrow(new ForbiddenException("RUB_SYSTEM_READ_ONLY", "fork"))
                    .given(service).deleteRubric(id);

            mockMvc.perform(delete("/v1/academic/rubrics/{u}", id))
                    .andExpect(status().isForbidden());
        }
    }
}