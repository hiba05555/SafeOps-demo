package ma.ensa.appointmentservice.web;

import ma.ensa.appointmentservice.domain.AppointmentService;
import ma.ensa.appointmentservice.domain.AppointmentStatus;
import ma.ensa.appointmentservice.security.CurrentUser;
import ma.ensa.appointmentservice.security.CurrentUserResolver;
import ma.ensa.appointmentservice.web.Views.AppointmentView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AppointmentControllerTest {

    private AppointmentService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AppointmentService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AppointmentController(service))
                .setCustomArgumentResolvers(new CurrentUserResolver())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createUsesGatewayIdentityAndReturnsCreatedAppointment() throws Exception {
        UUID id = UUID.fromString("ce9b27c9-52c0-4dd1-9ae3-f62f34f8e98f");
        Instant startsAt = Instant.parse("2099-01-01T10:00:00Z");
        when(service.create(any(), any())).thenReturn(new AppointmentView(id, "client-1", "clinic",
                1L, startsAt, "Consultation", AppointmentStatus.BOOKED, Instant.parse("2026-01-01T10:00:00Z")));

        mockMvc.perform(post("/api/appointments")
                        .header("X-Auth-Subject", "client-1")
                        .header("X-Auth-Username", "client")
                        .contentType("application/json")
                        .content("""
                                {"establishmentId":"clinic","queueId":1,"startsAt":"2099-01-01T10:00:00Z","reason":"Consultation"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.ownerSubject").value("client-1"))
                .andExpect(jsonPath("$.status").value("BOOKED"));

        var userCaptor = forClass(CurrentUser.class);
        verify(service).create(userCaptor.capture(), any());
        assertEquals("client-1", userCaptor.getValue().subject());
    }
}
