package org.mobilecms.api.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mobilecms.api.config.AppProperties;
import org.mobilecms.api.security.JwtService;
import org.mobilecms.api.service.AuthService;
import org.mobilecms.api.service.CalendarExportService;
import org.mobilecms.api.service.ContentService;
import org.mobilecms.api.service.FileService;
import org.mobilecms.api.service.ServiceResult;
import org.mobilecms.api.util.JsonFiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(CmsController.class)
@AutoConfigureMockMvc(addFilters = false)
class CmsControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContentService contentService;

    @MockBean
    private FileService fileService;

    @MockBean
    private AppProperties appProperties;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CalendarExportService calendarExportService;

    @MockBean
    private JsonFiles jsonFiles;

    @Test
    void listPublicContentTypes() throws Exception {
        Path publicDir = Path.of("/public");
        when(appProperties.getPublicDir()).thenReturn(publicDir);
        when(contentService.options(publicDir, "types.json"))
                .thenReturn(new ObjectMapper().readTree("[{\"type\":\"news\",\"labels\":[{\"i18n\":\"en\",\"label\":\"News\"}]}]"));

        mockMvc.perform(get("/mobilecmsapi/v50/webapi/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("news"));
    }
}
