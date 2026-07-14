package com.internship.coordinator.service;

import com.internship.coordinator.agent.DocumentExtractionAgent;
import com.internship.coordinator.support.SampleApplicationPdf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(
        properties = {
            "app.vertex-ai.enabled=true",
            "app.vertex-ai.project-id=${GCP_PROJECT_ID}",
            "app.vertex-ai.region=${GCP_REGION:europe-west1}",
            "app.vertex-ai.model-name=${GCP_GEMINI_MODEL:gemini-2.5-flash}"
        })
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_LIVE_TEST", matches = "true")
class DocumentExtractionLiveIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void liveExtractFromSamplePdf() throws Exception {
        byte[] pdfContent = SampleApplicationPdf.create();
        MockMultipartFile file = new MockMultipartFile(
                "file", "application.pdf", MediaType.APPLICATION_PDF_VALUE, pdfContent);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/cases").file(file))
                .andExpect(status().isCreated())
                .andReturn();

        String body = uploadResult.getResponse().getContentAsString();
        String caseId = body.substring(body.indexOf("\"caseId\":\"") + 10);
        caseId = caseId.substring(0, caseId.indexOf('"'));

        mockMvc.perform(post("/api/cases/{id}/extract", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("NEW")))
                .andExpect(jsonPath("$.studentName").exists())
                .andExpect(jsonPath("$.companyName").exists());

        mockMvc.perform(get("/api/cases/{id}", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentName").exists());
    }
}
