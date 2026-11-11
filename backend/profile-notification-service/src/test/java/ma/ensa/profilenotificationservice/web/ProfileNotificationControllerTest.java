package ma.ensa.profilenotificationservice.web;

import ma.ensa.profilenotificationservice.domain.ProfileNotificationService;
import ma.ensa.profilenotificationservice.security.CurrentUser;
import ma.ensa.profilenotificationservice.security.CurrentUserResolver;
import ma.ensa.profilenotificationservice.web.Views.PreferencesView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProfileNotificationControllerTest {

    private ProfileNotificationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(ProfileNotificationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProfileNotificationController(service))
                .setCustomArgumentResolvers(new CurrentUserResolver())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void updatePreferencesUsesGatewaySubject() throws Exception {
        when(service.updatePreferences(any(), any()))
                .thenReturn(new PreferencesView("client-1", false, true, false, "en"));

        mockMvc.perform(put("/api/profile/preferences")
                        .header("X-Auth-Subject", "client-1")
                        .header("X-Auth-Username", "client")
                        .contentType("application/json")
                        .content("""
                                {"sms":false,"email":true,"appointmentReminder":false,"language":"en"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("client-1"))
                .andExpect(jsonPath("$.email").value(true))
                .andExpect(jsonPath("$.language").value("en"));

        var userCaptor = forClass(CurrentUser.class);
        verify(service).updatePreferences(userCaptor.capture(), any());
        assertEquals("client-1", userCaptor.getValue().subject());
    }
}
