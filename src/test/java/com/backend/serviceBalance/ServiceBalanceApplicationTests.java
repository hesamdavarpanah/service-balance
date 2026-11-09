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

import java.time.Duration;
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

    @Test
    void transfer_sameRequestConcurrently_onlyOneSucceeds() throws Exception {
        WebTestClient client = webTestClient.mutate().responseTimeout(Duration.ofSeconds(60)).build();

        String sourceId = createTransferTestAccount(client);
        String destinationId = createTransferTestAccount(client);

        creditTransferTestAccount(client, sourceId);

        long sourceBefore = readTransferTestBalance(client, sourceId);
        long destinationBefore = readTransferTestBalance(client, destinationId);

        String transactionId = UUID.randomUUID().toString();
        long amount = 3_000L;

        Map<String, Object> request = Map.of("sourceAccountId", sourceId, "destinationAccountId", destinationId, "amount", amount, "transactionId", transactionId);

        List<TransferTestResult> results = executeConcurrentTransfers(client, request, request);

        long successCount = results.stream().filter(result -> result.status() == 200).count();

        long conflictCount = results.stream().filter(result -> result.status() == 409).count();

        long sourceAfter = readTransferTestBalance(client, sourceId);
        long destinationAfter = readTransferTestBalance(client, destinationId);

        assertAll(() -> assertEquals(1L, successCount, "Exactly one transfer must succeed: " + results), () -> assertEquals(1L, conflictCount, "The duplicate transfer must return 409: " + results), () -> assertEquals(sourceBefore - amount, sourceAfter, "Source must be debited only once"), () -> assertEquals(destinationBefore + amount, destinationAfter, "Destination must be credited only once"), () -> assertEquals(sourceBefore + destinationBefore, sourceAfter + destinationAfter, "Total balance must remain unchanged"));
    }

    @Test
    void transfer_twoAccountsConcurrently_bothSucceed() throws Exception {
        WebTestClient client = webTestClient.mutate().responseTimeout(Duration.ofSeconds(60)).build();

        String accountA = createTransferTestAccount(client);
        String accountB = createTransferTestAccount(client);

        creditTransferTestAccount(client, accountA);
        creditTransferTestAccount(client, accountB);

        long balanceABefore = readTransferTestBalance(client, accountA);
        long balanceBBefore = readTransferTestBalance(client, accountB);

        long amountAToB = 3_000L;
        long amountBToA = 2_000L;

        Map<String, Object> requestAToB = Map.of("sourceAccountId", accountA, "destinationAccountId", accountB, "amount", amountAToB, "transactionId", UUID.randomUUID().toString());

        Map<String, Object> requestBToA = Map.of("sourceAccountId", accountB, "destinationAccountId", accountA, "amount", amountBToA, "transactionId", UUID.randomUUID().toString());

        List<TransferTestResult> results = executeConcurrentTransfers(client, requestAToB, requestBToA);

        long balanceAAfter = readTransferTestBalance(client, accountA);
        long balanceBAfter = readTransferTestBalance(client, accountB);

        assertAll(() -> assertEquals(200, results.getFirst().status(), "A -> B failed: " + results.getFirst()), () -> assertEquals(200, results.get(1).status(), "B -> A failed: " + results.get(1)), () -> assertEquals(balanceABefore - amountAToB + amountBToA, balanceAAfter, "Account A balance is incorrect"), () -> assertEquals(balanceBBefore + amountAToB - amountBToA, balanceBAfter, "Account B balance is incorrect"), () -> assertEquals(balanceABefore + balanceBBefore, balanceAAfter + balanceBAfter, "Total balance must remain unchanged"));
    }

    private record TransferTestResult(int status, String body) {
    }

    private String transferTestBasePath() {
        return baseURL.replaceAll("/+$", "");
    }

    private String createTransferTestAccount(WebTestClient client) {
        String uniqueId = UUID.randomUUID().toString().replace("-", "");
        String username = "transfer_" + uniqueId;

        String phoneNumber = "09" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));

        Map<String, Object> request = Map.of("firstname", "Transfer", "lastname", "Test", "username", username, "email", username + "@example.com", "phoneNumber", phoneNumber, "status", "ACTIVE", "role", "USER", "password", "SecurePas$1");

        client.post().uri(transferTestBasePath() + "/accounts/create-account/").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).bodyValue(request).exchange().expectStatus().isCreated();

        JsonNode accounts = client.get().uri(transferTestBasePath() + "/accounts/retrieve-accounts/").accept(MediaType.APPLICATION_JSON).exchange().expectStatus().isOk().expectBody(JsonNode.class).returnResult().getResponseBody();

        assertNotNull(accounts, "Accounts response is null");
        assertTrue(accounts.isArray(), "Accounts response must be an array");

        for (JsonNode account : accounts) {
            if (username.equals(account.path("username").asString())) {
                String accountId = account.path("id").asString();

                assertFalse(accountId.isBlank(), "Created account ID is missing");

                return accountId;
            }
        }

        throw new AssertionError("Created account not found: " + username);
    }

    private void creditTransferTestAccount(WebTestClient client, String accountId) {
        client.post().uri(transferTestBasePath() + "/balances/credit/").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).bodyValue(Map.of("accountId", accountId, "amount", 10000L, "transactionId", UUID.randomUUID().toString())).exchange().expectStatus().isOk();
    }

    private long readTransferTestBalance(WebTestClient client, String accountId) {
        JsonNode response = client.post().uri(transferTestBasePath() + "/balances/get-balance/").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).bodyValue(Map.of("accountId", accountId)).exchange().expectStatus().isOk().expectBody(JsonNode.class).returnResult().getResponseBody();

        assertNotNull(response, "Balance response is null");
        assertTrue(response.hasNonNull("explanation"), "Balance is missing from response");

        return Long.parseLong(response.path("explanation").asString());
    }

    private List<TransferTestResult> executeConcurrentTransfers(WebTestClient client, Map<String, Object> firstRequest, Map<String, Object> secondRequest) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        List<Map<String, Object>> requests = List.of(firstRequest, secondRequest);

        List<Future<TransferTestResult>> futures = new ArrayList<>();

        try {
            for (Map<String, Object> request : requests) {
                futures.add(executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(10, TimeUnit.SECONDS))
                        throw new IllegalStateException("Timed out waiting for concurrent start");

                    var response = client.post().uri(transferTestBasePath() + "/balances/transfer/").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).bodyValue(request).exchange().expectBody(String.class).returnResult();

                    return new TransferTestResult(response.getStatus().value(), response.getResponseBody());
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Transfer workers did not become ready");

            start.countDown();

            List<TransferTestResult> results = new ArrayList<>();

            for (int i = 0; i < futures.size(); i++) {
                TransferTestResult result = futures.get(i).get(90, TimeUnit.SECONDS);

                results.add(result);

                Map<String, Object> request = requests.get(i);

                System.out.printf("Transfer #%d -> Transaction=%s | Source=%s | " + "Destination=%s | Amount=%s | Status=%d | Body=%s%n", i + 1, request.get("transactionId"), request.get("sourceAccountId"), request.get("destinationAccountId"), request.get("amount"), result.status(), result.body());
            }

            long successCount = results.stream().filter(result -> result.status() == 200).count();

            long conflictCount = results.stream().filter(result -> result.status() == 409).count();

            System.out.printf("Transfer summary -> Total=%d | Success=%d | Conflict=%d | Other=%d%n", results.size(), successCount, conflictCount, results.size() - successCount - conflictCount);

            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
}