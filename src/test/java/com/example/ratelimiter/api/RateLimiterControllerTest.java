package com.example.ratelimiter.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RateLimiterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowsThenRejectsOnceFull() throws Exception {
        // default capacity=5; fill with 5 allowed requests at t=0
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/requests")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\":\"burst\",\"timestamp\":0.0}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.allowed").value(true));
        }
        // 6th overflows → 429
        mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"burst\",\"timestamp\":0.0}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.allowed").value(false));
    }

    @Test
    void unknownUserBucketReturns404() throws Exception {
        mockMvc.perform(get("/users/nobody/bucket"))
                .andExpect(status().isNotFound());
    }

    @Test
    void blankUserIdReturns400() throws Exception {
        mockMvc.perform(post("/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"\",\"timestamp\":0.0}"))
                .andExpect(status().isBadRequest());
    }
}
