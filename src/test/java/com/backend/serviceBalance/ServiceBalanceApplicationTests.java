package com.backend.serviceBalance;

import com.backend.serviceBalance.accounts.payloads.responses.AccountResponse;
import com.backend.serviceBalance.transactions.models.enums.TransactionMethod;
import com.backend.serviceBalance.transactions.payloads.requests.CreateTransactionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void createTransaction_forFirstAccount_shouldReturnCreated() {
        var accounts = webTestClient.get().uri("/api/accounts/retrieve-accounts/").accept(MediaType.APPLICATION_JSON).exchange().expectStatus().isOk().expectBodyList(AccountResponse.class).returnResult().getResponseBody();

        assertNotNull(accounts, "Accounts response must not be null");
        assertFalse(accounts.isEmpty(), "At least one account must exist");

        var firstAccount = accounts.getFirst();

        var request = CreateTransactionRequest.builder().accountId(firstAccount.id()).transactionMethod(TransactionMethod.CREDIT).amount(10000L).build();

        webTestClient.post().uri("/api/transactions/create-transaction/").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).bodyValue(request).exchange().expectStatus().isCreated().expectBody().jsonPath("$.title").isEqualTo("Success").jsonPath("$.explanation").isEqualTo("Transaction created successfully");
    }

    @Test
    void creditFirstTransaction_threeConcurrentRequests_thenPrintBalance() throws Exception {
        String basePath = baseURL.replaceAll("/+$", "");
        JsonNode transactions = webTestClient.get().uri(basePath + "/transactions/retrieve-transactions/").accept(MediaType.APPLICATION_JSON).exchange().expectStatus().isOk().expectBody(JsonNode.class).returnResult().getResponseBody();

        assertNotNull(transactions, "Transactions response is null");
        assertTrue(transactions.isArray() && !transactions.isEmpty(), "At least one transaction must exist");

        JsonNode firstTransaction = transactions.get(0);

        String transactionId = firstTransaction.path("id").asString();
        String accountId = firstTransaction.path("accountId").asString();

        assertTrue(firstTransaction.hasNonNull("amount"), "Transaction amount is missing");

        assertFalse(transactionId.isBlank(), "Transaction ID is missing");
        assertFalse(accountId.isBlank(), "Account ID is missing");

        assertTrue(firstTransaction.hasNonNull("amount"), "Transaction amount is missing");

        long amount = firstTransaction.path("amount").asLong();

        Map<String, Object> creditRequest = Map.of("accountId", accountId, "transactionId", transactionId, "amount", amount);
        System.out.printf("Account: %s | Transaction: %s%n", accountId, transactionId);

        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<String>> futures = new ArrayList<>();

        try {
            for (int i = 1; i <= 3; i++) {
                final int requestNumber = i;

                futures.add(executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(10, TimeUnit.SECONDS))
                        throw new IllegalStateException("Timed out waiting for concurrent start");

                    var result = webTestClient.post().uri(basePath + "/balances/credit/").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).bodyValue(creditRequest).exchange().expectBody(String.class).returnResult();

                    return String.format("Credit #%d -> status=%s, body=%s", requestNumber, result.getStatus(), result.getResponseBody());
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Credit workers did not become ready");

            start.countDown();

            for (Future<String> future : futures) System.out.println(future.get(30, TimeUnit.SECONDS));

            JsonNode balanceResponse = webTestClient.post().uri(basePath + "/balances/get-balance/").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).bodyValue(Map.of("accountId", accountId)).exchange().expectStatus().isOk().expectBody(JsonNode.class).returnResult().getResponseBody();

            assertNotNull(balanceResponse, "Balance response is null");

            System.out.printf("Final balance for account %s: %s%n", accountId, balanceResponse.path("explanation").asString());

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void creditAllTransactions_concurrently_thenPrintBalances() throws Exception {
        String basePath = baseURL.replaceAll("/+$", "");
        WebTestClient client = webTestClient.mutate().responseTimeout(java.time.Duration.ofSeconds(60)).build();

        JsonNode transactions = client.get().uri(basePath + "/transactions/retrieve-transactions/").accept(MediaType.APPLICATION_JSON).exchange().expectStatus().isOk().expectBody(JsonNode.class).returnResult().getResponseBody();

        assertNotNull(transactions, "Transactions response is null");
        assertTrue(transactions.isArray(), "Transactions response must be an array");

        record CreditJob(String transactionId, String accountId, long amount, int expectedStatus) {
        }

        record CreditResult(CreditJob job, int actualStatus, String body) {
        }

        List<CreditJob> jobs = new ArrayList<>();
        java.util.Set<String> accountIds = new java.util.LinkedHashSet<>();

        for (JsonNode transaction : transactions) {
            String method = transaction.path("transactionMethod").asString();
            String status = transaction.path("transactionStatus").asString();

            if (!TransactionMethod.CREDIT.name().equals(method)) continue;

            if (!"PENDING".equals(status) && !"SUCCESS".equals(status)) {
                System.out.printf("Skipped transaction %s: status=%s%n", transaction.path("id").asString(), status);
                continue;
            }

            String transactionId = transaction.path("id").asString();
            String accountId = transaction.path("accountId").asString();

            assertFalse(transactionId.isBlank(), "Transaction ID is missing");
            assertFalse(accountId.isBlank(), "Account ID is missing");
            assertTrue(transaction.hasNonNull("amount"), "Transaction amount is missing");

            long amount = transaction.path("amount").asLong();

            assertTrue(amount > 0, "Credit amount must be positive");

            jobs.add(new CreditJob(transactionId, accountId, amount, "PENDING".equals(status) ? 200 : 409));

            accountIds.add(accountId);
        }

        assertFalse(jobs.isEmpty(), "No PENDING or SUCCESS CREDIT transactions found");

        int concurrency = Math.min(10, jobs.size());

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<CreditResult>> futures = new ArrayList<>();
        List<CreditResult> results = new ArrayList<>();

        try {
            for (CreditJob job : jobs) {
                futures.add(executor.submit(() -> {
                    if (!start.await(10, TimeUnit.SECONDS))
                        throw new IllegalStateException("Timed out waiting for concurrent start");

                    var response = client.post().uri(basePath + "/balances/credit/").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).bodyValue(Map.of("accountId", job.accountId(), "transactionId", job.transactionId(), "amount", job.amount())).exchange().expectBody(String.class).returnResult();

                    return new CreditResult(job, response.getStatus().value(), response.getResponseBody());
                }));
            }

            start.countDown();

            for (Future<CreditResult> future : futures) {
                CreditResult result = future.get(90, TimeUnit.SECONDS);
                results.add(result);

                System.out.printf("Transaction=%s | Account=%s | Amount=%d | Status=%d | Body=%s%n", result.job().transactionId(), result.job().accountId(), result.job().amount(), result.actualStatus(), result.body());
            }

            long successCount = results.stream().filter(result -> result.actualStatus() == 200).count();

            long conflictCount = results.stream().filter(result -> result.actualStatus() == 409).count();

            System.out.printf("%nTotal=%d | Success=%d | Conflict=%d | Other=%d%n", results.size(), successCount, conflictCount, results.size() - successCount - conflictCount);

            for (String accountId : accountIds) {
                JsonNode balanceResponse = client.post().uri(basePath + "/balances/get-balance/").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).bodyValue(Map.of("accountId", accountId)).exchange().expectStatus().isOk().expectBody(JsonNode.class).returnResult().getResponseBody();

                assertNotNull(balanceResponse, "Balance response is null");

                System.out.printf("Final balance | Account=%s | Balance=%s%n", accountId, balanceResponse.path("explanation").asString());
            }

            assertAll("Concurrent credit responses", results.stream().map(result -> () -> assertEquals(result.job().expectedStatus(), result.actualStatus(), "Unexpected response for transaction " + result.job().transactionId() + ": " + result.body())));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}