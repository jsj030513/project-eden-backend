package com.projecteden;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectEdenBackendApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void contextLoads() {
	}

	@Test
	void healthEndpointReturnsServiceStatus() throws Exception {
		mockMvc.perform(get("/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OK"))
				.andExpect(jsonPath("$.service").value("Project Eden Backend"))
				.andExpect(jsonPath("$.version").value("0.0.1"));
	}

}
