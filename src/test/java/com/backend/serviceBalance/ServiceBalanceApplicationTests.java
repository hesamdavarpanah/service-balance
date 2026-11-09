package com.backend.serviceBalance;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class ServiceBalanceApplicationTests {

    @Autowired
    private WebTestClient webTestClient;

    @Value("${base-url}")
    private String baseURL;

    @Test
    void createAccount_shouldReturnCreated() {
        String uniqueId = UUID.randomUUID().toString().replace("-", "");

        String phoneNumber = "09" + String.format("%09d", java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L));

        Map<String, Object> request = Map.of("firstname", "Hesam", "lastname", "Davarpanah", "username", "test_" + uniqueId, "email", "test_" + uniqueId + "@example.com", "phoneNumber", phoneNumber, "status", "ACTIVE", "role", "USER", "password", "SecurePas$1");

        webTestClient.post().uri(baseURL + "/accounts/create-account/").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).bodyValue(request).exchange().expectStatus().isCreated().expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON).expectBody().jsonPath("$.title").isEqualTo("Success").jsonPath("$.explanation").isEqualTo("User created successfully");
    }

}