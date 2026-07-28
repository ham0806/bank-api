package dev.hambacon.bank;

import dev.hambacon.bank.adapter.in.web.ApiDtos.AccountResponse;
import dev.hambacon.bank.adapter.in.web.ApiDtos.AmountRequest;
import dev.hambacon.bank.adapter.in.web.ApiDtos.CreateAccountRequest;
import dev.hambacon.bank.adapter.in.web.ApiDtos.CreateTransferRequest;
import dev.hambacon.bank.adapter.in.web.ApiDtos.OperationResponse;
import dev.hambacon.bank.adapter.in.web.ApiDtos.TransferResponse;
import dev.hambacon.bank.adapter.out.integration.MockExternalSettlementAdapter;
import dev.hambacon.bank.worker.OutboxProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Sql(statements = {
        "DELETE FROM outbox_events",
        "DELETE FROM idempotency_records",
        "DELETE FROM ledger_entries",
        "DELETE FROM ledger_transactions",
        "DELETE FROM transfers",
        "DELETE FROM accounts WHERE account_number <> 'SYSTEM-CLEARING'",
        "UPDATE accounts SET balance_minor = 0, reserved_minor = 0, version = 0 WHERE account_number = 'SYSTEM-CLEARING'"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class BankApiIntegrationTest {
    @Autowired TestRestTemplate restTemplate;
    @Autowired OutboxProcessor outboxProcessor;
    @Autowired MockExternalSettlementAdapter externalSettlementAdapter;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetExternalAdapter() {
        externalSettlementAdapter.reset();
    }

    @Test
    void 同じ入金リクエストを再送しても二重計上されない() {
        var account = createAccount("A-100");
        var first = postAmount("/api/accounts/" + account.id() + "/deposits", 1_000, "deposit-1");
        var second = postAmount("/api/accounts/" + account.id() + "/deposits", 1_000, "deposit-1");

        assertThat(first.getBody().transactionId()).isEqualTo(second.getBody().transactionId());
        assertThat(getAccount(account.id()).balanceMinor()).isEqualTo(1_000);
    }

    @Test
    void 同じ冪等キーの並列入金でも一度だけ計上される() throws Exception {
        var account = createAccount("A-107");
        var start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentDeposit(account.id(), start));
            var second = executor.submit(() -> concurrentDeposit(account.id(), start));
            start.countDown();
            var firstResponse = first.get();
            var secondResponse = second.get();
            assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(firstResponse.getBody().transactionId()).isEqualTo(secondResponse.getBody().transactionId());
        }
        assertThat(getAccount(account.id()).balanceMinor()).isEqualTo(1_000);
    }

    @Test
    void 顧客APIからシステム口座を操作できない() {
        var headers = new HttpHeaders();
        headers.set("Idempotency-Key", "system-account-operation");
        var response = restTemplate.exchange(
                "/api/accounts/00000000-0000-0000-0000-000000000001/deposits",
                HttpMethod.POST,
                new HttpEntity<>(new AmountRequest(1_000), headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void 全口座で残高と仕訳合計が一致する() {
        var account = createAccount("A-108");
        postAmount("/api/accounts/" + account.id() + "/deposits", 1_000, "deposit-reconcile");
        postAmount("/api/accounts/" + account.id() + "/withdrawals", 250, "withdraw-reconcile");

        var rows = jdbcTemplate.queryForList("""
                SELECT a.account_number, a.balance_minor,
                       COALESCE(SUM(le.amount_minor), 0) AS ledger_sum
                  FROM accounts a
                  LEFT JOIN ledger_entries le ON le.account_id = a.id
                 GROUP BY a.id, a.account_number, a.balance_minor
                """);

        assertThat(rows).isNotEmpty().allSatisfy(row ->
                assertThat(((Number) row.get("balance_minor")).longValue())
                        .isEqualTo(((Number) row.get("ledger_sum")).longValue()));
    }

    @Test
    void 同じ冪等キーに異なる内容を指定すると409になる() {
        var account = createAccount("A-101");
        postAmount("/api/accounts/" + account.id() + "/deposits", 1_000, "conflict-1");
        var headers = new HttpHeaders();
        headers.set("Idempotency-Key", "conflict-1");
        var response = restTemplate.exchange("/api/accounts/" + account.id() + "/deposits", HttpMethod.POST,
                new HttpEntity<>(new AmountRequest(2_000), headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void 振込はOutbox処理後に仕訳と残高へ反映される() {
        var source = createAccount("A-102");
        var destination = createAccount("A-103");
        postAmount("/api/accounts/" + source.id() + "/deposits", 1_000, "deposit-transfer");

        var response = createTransfer(source.id(), destination.id(), 600, "transfer-1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().status()).isEqualTo("PENDING");
        assertThat(getAccount(source.id()).balanceMinor()).isEqualTo(1_000);
        assertThat(getAccount(source.id()).reservedMinor()).isEqualTo(600);

        outboxProcessor.processOne();

        assertThat(getTransfer(response.getBody().id()).getBody().status()).isEqualTo("COMPLETED");
        assertThat(getAccount(source.id()).balanceMinor()).isEqualTo(400);
        assertThat(getAccount(destination.id()).balanceMinor()).isEqualTo(600);
        assertThat(getAccount(source.id()).reservedMinor()).isZero();
    }

    @Test
    void 外部連携の恒久失敗では予約を解除して残高を維持する() {
        var source = createAccount("A-104");
        var destination = createAccount("A-105");
        postAmount("/api/accounts/" + source.id() + "/deposits", 1_000, "deposit-failure");
        var response = createTransfer(source.id(), destination.id(), 600, "transfer-failure");

        externalSettlementAdapter.failNextPermanently();
        outboxProcessor.processOne();

        assertThat(getTransfer(response.getBody().id()).getBody().status()).isEqualTo("FAILED");
        assertThat(getAccount(source.id()).balanceMinor()).isEqualTo(1_000);
        assertThat(getAccount(source.id()).reservedMinor()).isZero();
        assertThat(getAccount(destination.id()).balanceMinor()).isZero();
    }

    @Test
    void 外部連携の一時失敗後に再試行すると振込が完了する() {
        var source = createAccount("A-109");
        var destination = createAccount("A-110");
        postAmount("/api/accounts/" + source.id() + "/deposits", 1_000, "deposit-retry");
        var response = createTransfer(source.id(), destination.id(), 600, "transfer-retry");

        externalSettlementAdapter.failNextRetryably();
        assertThat(outboxProcessor.processOne()).isTrue();

        var scheduledRetry = jdbcTemplate.queryForMap(
                "SELECT status, attempts FROM outbox_events WHERE aggregate_id = ?", response.getBody().id());
        assertThat(scheduledRetry.get("status")).isEqualTo("PENDING");
        assertThat(((Number) scheduledRetry.get("attempts")).intValue()).isEqualTo(1);

        jdbcTemplate.update("UPDATE outbox_events SET available_at = CURRENT_TIMESTAMP WHERE aggregate_id = ?",
                response.getBody().id());
        assertThat(outboxProcessor.processOne()).isTrue();

        assertThat(getTransfer(response.getBody().id()).getBody().status()).isEqualTo("COMPLETED");
        assertThat(getAccount(source.id()).balanceMinor()).isEqualTo(400);
        assertThat(getAccount(source.id()).reservedMinor()).isZero();
        assertThat(getAccount(destination.id()).balanceMinor()).isEqualTo(600);
        assertThat(externalSettlementAdapter.attemptCount()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM outbox_events WHERE aggregate_id = ?", String.class, response.getBody().id()))
                .isEqualTo("DONE");
    }

    @Test
    void 同時出金では一方だけが成功し残高が負にならない() throws Exception {
        var account = createAccount("A-106");
        postAmount("/api/accounts/" + account.id() + "/deposits", 1_000, "deposit-concurrency");
        var start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentWithdraw(account.id(), "withdraw-a", start));
            var second = executor.submit(() -> concurrentWithdraw(account.id(), "withdraw-b", start));
            start.countDown();
            var successes = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
            assertThat(successes).isEqualTo(1);
        }
        assertThat(getAccount(account.id()).balanceMinor()).isZero();
    }

    private boolean concurrentWithdraw(UUID accountId, String key, CountDownLatch start) {
        try {
            start.await();
            return postAmount("/api/accounts/" + accountId + "/withdrawals", 1_000, key).getStatusCode().is2xxSuccessful();
        } catch (Exception exception) {
            return false;
        }
    }

    private ResponseEntity<OperationResponse> concurrentDeposit(UUID accountId, CountDownLatch start) {
        try {
            start.await();
            return postAmount("/api/accounts/" + accountId + "/deposits", 1_000, "parallel-deposit");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private AccountResponse createAccount(String number) {
        var response = restTemplate.postForEntity("/api/accounts", new CreateAccountRequest(number), AccountResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ResponseEntity<OperationResponse> postAmount(String path, long amount, String key) {
        var headers = new HttpHeaders();
        headers.set("Idempotency-Key", key);
        return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(new AmountRequest(amount), headers), OperationResponse.class);
    }

    private ResponseEntity<TransferResponse> createTransfer(UUID source, UUID destination, long amount, String key) {
        var headers = new HttpHeaders();
        headers.set("Idempotency-Key", key);
        return restTemplate.exchange("/api/transfers", HttpMethod.POST,
                new HttpEntity<>(new CreateTransferRequest(source, destination, amount), headers), TransferResponse.class);
    }

    private AccountResponse getAccount(UUID accountId) {
        return restTemplate.getForObject("/api/accounts/" + accountId, AccountResponse.class);
    }

    private ResponseEntity<TransferResponse> getTransfer(UUID transferId) {
        return restTemplate.getForEntity("/api/transfers/" + transferId, TransferResponse.class);
    }
}
