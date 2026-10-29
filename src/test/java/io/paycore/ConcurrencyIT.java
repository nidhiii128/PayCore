package io.paycore;

import com.fasterxml.jackson.databind.JsonNode;
import io.paycore.ledger.ReconciliationService;
import io.paycore.support.ApiClient.User;
import io.paycore.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyIT extends IntegrationTest {

    @Autowired
    ReconciliationService reconciliation;

    @Test
    void opposingTransfersDoNotDeadlockOrLoseUpdates() throws Exception {
        User alice = api.register();
        User bob = api.register();
        UUID a = api.walletFor(alice, "USD");
        UUID b = api.walletFor(bob, "USD");
        api.deposit(alice, a, 100_000);
        api.deposit(bob, b, 100_000);

        List<Callable<ResponseEntity<JsonNode>>> tasks = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            tasks.add(() -> api.transfer(alice, a, b, 100));
            tasks.add(() -> api.transfer(bob, b, a, 100));
        }

        Map<HttpStatusCode, Long> statuses = runConcurrently(tasks);

        assertThat(statuses).containsOnlyKeys(HttpStatus.CREATED);
        assertThat(api.balance(alice, a)).isEqualTo(100_000);
        assertThat(api.balance(bob, b)).isEqualTo(100_000);
        assertThat(reconciliation.run().isClean()).isTrue();
    }

    @Test
    void racingWithdrawalsCannotOverdraw() throws Exception {
        User alice = api.register();
        User bob = api.register();
        UUID a = api.walletFor(alice, "USD");
        UUID b = api.walletFor(bob, "USD");
        api.deposit(alice, a, 1_000);

        // 40 concurrent attempts to spend 100 from a balance of 1,000: exactly 10 may succeed.
        List<Callable<ResponseEntity<JsonNode>>> tasks = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            tasks.add(i % 2 == 0 ? () -> api.transfer(alice, a, b, 100) : () -> api.withdraw(alice, a, 100));
        }

        Map<HttpStatusCode, Long> statuses = runConcurrently(tasks);

        assertThat(statuses.get(HttpStatus.CREATED)).isEqualTo(10);
        assertThat(statuses.get(HttpStatus.UNPROCESSABLE_ENTITY)).isEqualTo(30);
        assertThat(api.balance(alice, a)).isZero();
        assertThat(reconciliation.run().isClean()).isTrue();
    }

    private static Map<HttpStatusCode, Long> runConcurrently(List<Callable<ResponseEntity<JsonNode>>> tasks)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(32)) {
            for (Callable<ResponseEntity<JsonNode>> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
        }
        List<HttpStatusCode> codes = new ArrayList<>();
        for (Future<ResponseEntity<JsonNode>> future : futures) {
            codes.add(future.get().getStatusCode());
        }
        return codes.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }
}
