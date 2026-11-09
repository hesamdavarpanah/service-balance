package com.backend.serviceBalance;

import com.backend.serviceBalance.transactions.models.Transaction;
import com.backend.serviceBalance.transactions.models.enums.TransactionMethod;
import com.backend.serviceBalance.transactions.models.enums.TransactionStatus;
import com.backend.serviceBalance.transactions.repositories.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.JsonNode;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class DebitIntegrationTests {

    private static final int OK = 200;
    private static final int BAD_REQUEST = 400;
    private static final int CONFLICT = 409;
    private static final int INSUFFICIENT_BALANCE = 422;
    private static final Duration DB_TIMEOUT = Duration.ofSeconds(15);
    private static final Path DEBIT_SCENARIO_FILE = Path.of(".test-data", "debit-single-account.properties");

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private R2dbcEntityTemplate entityTemplate;

    @Autowired
    private TransactionRepository transactionRepository;

    @Value("${base-url}")
    private String baseURL;

    private record Job(String accountId, String transactionId, long amount) {
    }

    private record Result(Job job, int status, String body) {
    }

    @Test
    void debit_shouldDecreaseBalanceAndCompleteTransaction() {
        String accountId = fundedAccount(10_000);

        Job job = pendingDebit(accountId, 3_000);

        Result result = debit(job);

        assertEquals(OK, result.status(), result.body());
        assertEquals(7_000, balance(accountId));
        assertTransactionStatus(job, TransactionStatus.SUCCESS);
    }

    @Test
    void debit_equalToBalance_shouldLeaveZeroBalance() {
        String accountId = fundedAccount(10_000);

        Job job = pendingDebit(accountId, 10_000);

        Result result = debit(job);

        assertEquals(OK, result.status(), result.body());
        assertEquals(0, balance(accountId));
        assertTransactionStatus(job, TransactionStatus.SUCCESS);
    }

    @Test
    void debit_zeroAndNegativeAmounts_shouldFailWithoutChangingBalance() {
        String accountId = fundedAccount(10_000);

        for (long amount : new long[]{0, -1, -10_000}) {
            Job job = new Job(accountId, newId(), amount);

            Result result = debit(job);

            assertEquals(BAD_REQUEST, result.status(), result.body());
            assertEquals(10_000, balance(accountId));

            assertNull(transactionRepository.findById(job.transactionId()).block(DB_TIMEOUT), "Invalid amount must not create a transaction");
        }
    }

    @Test
    void debit_moreThanBalance_shouldFailAndRollback() {
        String accountId = fundedAccount(10_000);
        Job existingTransaction = pendingDebit(accountId, 10_001);

        Result first = debit(existingTransaction);

        assertEquals(INSUFFICIENT_BALANCE, first.status(), first.body());
        assertEquals(10_000, balance(accountId));
        assertTransactionStatus(existingTransaction, TransactionStatus.PENDING);
        Job newTransaction = new Job(accountId, newId(), 10_001);

        Result second = debit(newTransaction);

        assertEquals(INSUFFICIENT_BALANCE, second.status(), second.body());
        assertEquals(10_000, balance(accountId));

        assertNull(transactionRepository.findById(newTransaction.transactionId()).block(DB_TIMEOUT), "New PENDING transaction must roll back on insufficient balance");
    }

    @Test
    void debit_completedTransaction_shouldReturnConflictWithoutSecondWithdrawal() {
        String accountId = fundedAccount(10_000);
        Job job = pendingDebit(accountId, 3_000);

        Result first = debit(job);
        Result second = debit(job);

        assertAll(() -> assertEquals(OK, first.status(), first.body()), () -> assertEquals(CONFLICT, second.status(), second.body()), () -> assertEquals(7_000, balance(accountId)), () -> assertTransactionStatus(job, TransactionStatus.SUCCESS));
    }

    @Test
    void debit_sameTransactionWithDifferentAmount_shouldReturnConflict() {
        String accountId = fundedAccount(10_000);
        Job original = pendingDebit(accountId, 3_000);

        Job conflicting = new Job(accountId, original.transactionId(), 4_000);

        Result result = debit(conflicting);

        assertEquals(CONFLICT, result.status(), result.body());
        assertEquals(10_000, balance(accountId));
        assertTransactionStatus(original, TransactionStatus.PENDING);
    }

    @Test
    void debit_creditTransactionId_shouldReturnConflict() {
        String accountId = fundedAccount(10_000);

        String transactionId = insertPending(accountId, 3_000, TransactionMethod.CREDIT);

        Job job = new Job(accountId, transactionId, 3_000);

        Result result = debit(job);

        assertEquals(CONFLICT, result.status(), result.body());
        assertEquals(10_000, balance(accountId));
        assertTransactionStatus(job, TransactionStatus.PENDING);
    }

    @Test
    void debit_sameTransaction_threeConcurrentRequests_shouldWithdrawExactlyOnce() throws Exception {

        String accountId = fundedAccount(10_000);
        Job job = pendingDebit(accountId, 3_000);

        List<Result> results = concurrently(List.of(job, job, job));

        assertAll("Same transaction race", () -> assertEquals(1, countStatus(results, OK), describe(results)), () -> assertEquals(2, countStatus(results, CONFLICT), describe(results)), () -> assertEquals(7_000, balance(accountId)), () -> assertTransactionStatus(job, TransactionStatus.SUCCESS));

        assertNoUnexpectedStatus(results, CONFLICT);
    }

    @Test
    void debit_newTransactionId_concurrentRequests_shouldCreateAndWithdrawExactlyOnce() throws Exception {

        String accountId = fundedAccount(10_000);

        Job job = new Job(accountId, newId(), 3_000);

        List<Result> results = concurrently(List.of(job, job, job));

        assertAll("Concurrent creation of the same transaction", () -> assertEquals(1, countStatus(results, OK), describe(results)), () -> assertEquals(2, countStatus(results, CONFLICT), describe(results)), () -> assertEquals(7_000, balance(accountId)), () -> assertTransactionStatus(job, TransactionStatus.SUCCESS));

        assertNoUnexpectedStatus(results, CONFLICT);
    }

    @Test
    void debit_allPendingTransactions_concurrently_shouldSubtractExactTotal() throws Exception {

        assertTrue(Files.exists(DEBIT_SCENARIO_FILE), "First run createDebitTransaction_shouldReturnCreated");

        Properties scenario = new Properties();

        try (InputStream input = Files.newInputStream(DEBIT_SCENARIO_FILE)) {
            scenario.load(input);
        }

        String accountId = scenario.getProperty("accountId");

        long initialBalance = Long.parseLong(scenario.getProperty("initialBalance"));

        long amount = Long.parseLong(scenario.getProperty("amount"));

        int transactionCount = Integer.parseInt(scenario.getProperty("transactionCount"));

        List<Job> jobs = new ArrayList<>();

        for (int i = 0; i < transactionCount; i++)
            jobs.add(new Job(accountId, scenario.getProperty("transactionId." + i), amount));

        assertEquals(initialBalance, balance(accountId), "Prepared account balance has changed, or debits already ran");

        for (Job job : jobs) assertTransactionStatus(job, TransactionStatus.PENDING);

        List<Result> results = concurrently(jobs);

        for (int i = 0; i < results.size(); i++) {
            Result result = results.get(i);
            Job job = result.job();

            System.out.printf("Debit #%d -> account=%s, transaction=%s, amount=%d, status=%d, body=%s%n", i + 1, job.accountId(), job.transactionId(), job.amount(), result.status(), result.body());
        }

        assertAll("Concurrent debit responses", results.stream().map(result -> () -> assertEquals(OK, result.status(), "Transaction=" + result.job().transactionId() + " | Body=" + result.body())));

        long expectedBalance = initialBalance - transactionCount * amount;

        assertEquals(expectedBalance, balance(accountId), "Concurrent debits must not lose balance updates");

        for (Job job : jobs) assertTransactionStatus(job, TransactionStatus.SUCCESS);

        List<Result> repeated = concurrently(jobs);

        for (int i = 0; i < repeated.size(); i++) {
            Result result = repeated.get(i);
            Job job = result.job();

            System.out.printf("Repeated Debit #%d -> account=%s, transaction=%s, amount=%d, status=%d, body=%s%n", i + 1, job.accountId(), job.transactionId(), job.amount(), result.status(), result.body());
        }

        assertAll("Repeated transactions", repeated.stream().map(result -> () -> assertEquals(CONFLICT, result.status(), result.body())));

        assertEquals(expectedBalance, balance(accountId));

        System.out.printf("Debited SAME account=%s | Before=%d | After=%d%n", accountId, initialBalance, expectedBalance);
    }

    @Test
    void debit_concurrentOverdraftRace_shouldAllowOnlyAffordableWithdrawals() throws Exception {

        String accountId = fundedAccount(10_000);

        List<Job> jobs = List.of(pendingDebit(accountId, 6_000), pendingDebit(accountId, 6_000), pendingDebit(accountId, 6_000));

        List<Result> results = concurrently(jobs);

        assertAll("Overdraft race", () -> assertEquals(1, countStatus(results, OK), describe(results)), () -> assertEquals(2, countStatus(results, INSUFFICIENT_BALANCE), describe(results)), () -> assertEquals(4_000, balance(accountId), "Balance must never become negative or lose updates"));

        assertNoUnexpectedStatus(results, INSUFFICIENT_BALANCE);

        for (Result result : results)
            assertTransactionStatus(result.job(), result.status() == OK ? TransactionStatus.SUCCESS : TransactionStatus.PENDING);
    }

    @Test
    void debit_conflictingAmountsRace_shouldOnlyExecuteMatchingOperation() throws Exception {

        String accountId = fundedAccount(10_000);
        Job valid = pendingDebit(accountId, 3_000);

        Job invalid = new Job(accountId, valid.transactionId(), 4_000);

        List<Result> results = concurrently(List.of(valid, invalid));

        assertAll("Idempotency conflict race", results.stream().map(result -> () -> assertEquals(result.job().amount() == 3_000 ? OK : CONFLICT, result.status(), result.body())));

        assertEquals(7_000, balance(accountId));
        assertTransactionStatus(valid, TransactionStatus.SUCCESS);
    }

    private String path(String suffix) {
        return baseURL.replaceAll("/+$", "") + suffix;
    }

    private WebTestClient client() {
        return webTestClient.mutate().responseTimeout(Duration.ofSeconds(60)).build();
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }

    private String fundedAccount(long initialBalance) {
        String accountId = createFreshAccount();

        assertEquals(0, balance(accountId), "A newly created test account must have zero balance");

        String transactionId = insertPending(accountId, initialBalance, TransactionMethod.CREDIT);

        var response = client().post().uri(path("/balances/credit/")).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).bodyValue(Map.of("accountId", accountId, "transactionId", transactionId, "amount", initialBalance)).exchange().expectBody(String.class).returnResult();

        assertEquals(OK, response.getStatus().value(), response.getResponseBody());

        assertEquals(initialBalance, balance(accountId));

        return accountId;
    }

    private String createFreshAccount() {
        String unique = UUID.randomUUID().toString().replace("-", "");
        String username = "debit_test_" + unique;

        String phoneNumber = "09" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));

        client().post().uri(path("/accounts/create-account/")).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).bodyValue(Map.of("firstname", "Debit", "lastname", "IntegrationTest", "username", username, "email", username + "@example.com", "phoneNumber", phoneNumber, "status", "ACTIVE", "role", "USER", "password", "SecurePas$1")).exchange().expectStatus().isCreated();

        JsonNode accounts = client().get().uri(path("/accounts/retrieve-accounts/")).accept(MediaType.APPLICATION_JSON).exchange().expectStatus().isOk().expectBody(JsonNode.class).returnResult().getResponseBody();

        assertNotNull(accounts);
        assertTrue(accounts.isArray());

        for (JsonNode account : accounts) {
            if (username.equals(account.path("username").asString())) {
                String id = account.path("id").asString();

                assertFalse(id.isBlank());

                return id;
            }
        }

        throw new AssertionError("Created account was not found: " + username);
    }

    private Job pendingDebit(String accountId, long amount) {
        return new Job(accountId, insertPending(accountId, amount, TransactionMethod.DEBIT), amount);
    }

    private String insertPending(String accountId, long amount, TransactionMethod method) {
        String transactionId = newId();
        LocalDateTime now = LocalDateTime.now();

        Transaction transaction = Transaction.builder().id(transactionId).accountId(accountId).amount(amount).transactionMethod(method).transactionStatus(TransactionStatus.PENDING).createdAt(now).updatedAt(now).build();

        assertNotNull(entityTemplate.insert(transaction).block(DB_TIMEOUT));

        return transactionId;
    }

    private Result debit(Job job) {
        var response = client().post().uri(path("/balances/debit/")).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).bodyValue(Map.of("accountId", job.accountId(), "transactionId", job.transactionId(), "amount", job.amount())).exchange().expectBody(String.class).returnResult();

        return new Result(job, response.getStatus().value(), response.getResponseBody());
    }

    private long balance(String accountId) {
        JsonNode response = client().post().uri(path("/balances/get-balance/")).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).bodyValue(Map.of("accountId", accountId)).exchange().expectStatus().isOk().expectBody(JsonNode.class).returnResult().getResponseBody();

        assertNotNull(response);
        assertTrue(response.hasNonNull("explanation"));

        return Long.parseLong(response.path("explanation").asString());
    }

    private void assertTransactionStatus(Job job, TransactionStatus expected) {
        Transaction transaction = transactionRepository.findById(job.transactionId()).block(DB_TIMEOUT);

        assertNotNull(transaction, "Transaction missing: " + job.transactionId());

        assertEquals(expected, transaction.getTransactionStatus());
        assertEquals(job.accountId(), transaction.getAccountId());
        assertEquals(Long.valueOf(job.amount()), transaction.getAmount());
    }

    private List<Result> concurrently(List<Job> jobs) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(jobs.size());

        CountDownLatch ready = new CountDownLatch(jobs.size());
        CountDownLatch start = new CountDownLatch(1);

        List<Future<Result>> futures = new ArrayList<>();

        try {
            for (Job job : jobs) {
                futures.add(executor.submit(() -> {
                    ready.countDown();

                    if (!start.await(15, TimeUnit.SECONDS))
                        throw new IllegalStateException("Timed out waiting for concurrent start");

                    return debit(job);
                }));
            }

            assertTrue(ready.await(15, TimeUnit.SECONDS), "Concurrent workers did not become ready");

            start.countDown();

            List<Result> results = new ArrayList<>();

            for (Future<Result> future : futures) results.add(future.get(90, TimeUnit.SECONDS));

            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private long countStatus(List<Result> results, int status) {
        return results.stream().filter(result -> result.status() == status).count();
    }

    private void assertNoUnexpectedStatus(List<Result> results, int secondAllowed) {
        assertAll("No unhandled concurrency errors", results.stream().map(result -> () -> assertTrue(result.status() == DebitIntegrationTests.OK || result.status() == secondAllowed, "Unexpected response for " + result.job().transactionId() + ": status=" + result.status() + ", body=" + result.body())));
    }

    private String describe(List<Result> results) {
        return results.toString();
    }

    @Test
    void createDebitTransaction_shouldReturnCreated() throws Exception {
        assertFalse(Files.exists(DEBIT_SCENARIO_FILE), "Scenario already exists. Run the debit step, or delete " + DEBIT_SCENARIO_FILE + " to prepare a new scenario.");

        long initialBalance = 100_000;
        long amount = 1_000;
        int transactionCount = 10;

        String accountId = fundedAccount(initialBalance);

        Properties scenario = new Properties();
        scenario.setProperty("accountId", accountId);
        scenario.setProperty("initialBalance", String.valueOf(initialBalance));
        scenario.setProperty("amount", String.valueOf(amount));
        scenario.setProperty("transactionCount", String.valueOf(transactionCount));

        for (int i = 0; i < transactionCount; i++) {
            Job job = pendingDebit(accountId, amount);

            assertTransactionStatus(job, TransactionStatus.PENDING);

            scenario.setProperty("transactionId." + i, job.transactionId());
        }

        assertEquals(initialBalance, balance(accountId));

        Files.createDirectories(DEBIT_SCENARIO_FILE.getParent());

        try (OutputStream output = Files.newOutputStream(DEBIT_SCENARIO_FILE)) {
            scenario.store(output, "Prepared single-account debit scenario");
        }

        System.out.printf("Prepared account=%s | Balance=%d | Pending transactions=%d%n", accountId, initialBalance, transactionCount);

        System.out.println("Scenario saved to: " + DEBIT_SCENARIO_FILE.toAbsolutePath());
    }

    @Test
    void debit_multipleAccounts_concurrently_shouldSubtractExactTotals() throws Exception {

        int accountCount = 5;
        int transactionsPerAccount = 4;
        long initialBalance = 20_000;
        long debitAmount = 1_000;

        List<String> accountIds = new ArrayList<>();
        List<Job> jobs = new ArrayList<>();

        for (int i = 0; i < accountCount; i++) {
            String accountId = fundedAccount(initialBalance);
            accountIds.add(accountId);

            for (int j = 0; j < transactionsPerAccount; j++) jobs.add(pendingDebit(accountId, debitAmount));
        }

        List<Result> results = concurrently(jobs);

        assertEquals(accountCount * transactionsPerAccount, results.size());

        assertAll("Concurrent debit across multiple accounts", results.stream().map(result -> () -> assertEquals(OK, result.status(), "Account=" + result.job().accountId() + " | Transaction=" + result.job().transactionId() + " | Body=" + result.body())));

        long expectedBalance = initialBalance - transactionsPerAccount * debitAmount;

        for (String accountId : accountIds) {
            long actualBalance = balance(accountId);

            System.out.printf("Account=%s | Expected balance=%d | Actual balance=%d%n", accountId, expectedBalance, actualBalance);

            assertEquals(expectedBalance, actualBalance, "Lost update or incorrect withdrawal for account " + accountId);
        }

        for (Job job : jobs) assertTransactionStatus(job, TransactionStatus.SUCCESS);

        List<Result> repeatedResults = concurrently(jobs);

        assertAll("Repeated transactions must return conflict", repeatedResults.stream().map(result -> () -> assertEquals(CONFLICT, result.status(), "Transaction=" + result.job().transactionId() + " | Body=" + result.body())));

        for (String accountId : accountIds)
            assertEquals(expectedBalance, balance(accountId), "Repeated requests must not change balance: " + accountId);
    }
}