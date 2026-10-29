package io.paycore;

import com.fasterxml.jackson.databind.JsonNode;
import io.paycore.ledger.ReconciliationService;
import io.paycore.support.ApiClient.User;
import io.paycore.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentFlowIT extends IntegrationTest {

    @Autowired
    ReconciliationService reconciliation;

    @Test
    void depositTransferAndWithdrawMoveMoneyAndProduceAStatement() {
        User alice = api.register();
        User bob = api.register();
        UUID aliceWallet = api.walletFor(alice, "usd");
        UUID bobWallet = api.walletFor(bob, "USD");

        assertThat(api.deposit(alice, aliceWallet, 10_000).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<JsonNode> transfer = api.transfer(alice, aliceWallet, bobWallet, 2_500);
        assertThat(transfer.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(transfer.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("false");
        JsonNode tx = transfer.getBody();
        assertThat(tx.get("type").asText()).isEqualTo("TRANSFER");
        assertThat(tx.get("currency").asText()).isEqualTo("USD");
        assertThat(tx.get("amount").asLong()).isEqualTo(2_500);
        assertThat(tx.has("balance")).isFalse();

        assertThat(api.withdraw(bob, bobWallet, 500).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(api.balance(alice, aliceWallet)).isEqualTo(7_500);
        assertThat(api.balance(bob, bobWallet)).isEqualTo(2_000);

        JsonNode statement = api.get(alice, "/api/v1/wallets/" + aliceWallet + "/transactions").getBody();
        assertThat(statement.get("totalElements").asLong()).isEqualTo(2);
        JsonNode newest = statement.get("content").get(0);
        assertThat(newest.get("type").asText()).isEqualTo("TRANSFER");
        assertThat(newest.get("direction").asText()).isEqualTo("DEBIT");
        assertThat(newest.get("balanceAfter").asLong()).isEqualTo(7_500);
        assertThat(statement.get("content").get(1).get("balanceAfter").asLong()).isEqualTo(10_000);

        assertThat(reconciliation.run().isClean()).isTrue();
    }

    @Test
    void bothPartiesCanSeeATransactionButNobodyElseCan() {
        User alice = api.register();
        User bob = api.register();
        User eve = api.register();
        UUID aliceWallet = api.walletFor(alice, "EUR");
        UUID bobWallet = api.walletFor(bob, "EUR");
        api.deposit(alice, aliceWallet, 1_000);
        String txId = api.transfer(alice, aliceWallet, bobWallet, 100).getBody().get("id").asText();

        assertThat(api.get(alice, "/api/v1/transactions/" + txId).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> asBob = api.get(bob, "/api/v1/transactions/" + txId);
        assertThat(asBob.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asBob.getBody().get("fromWalletId").asText()).isEqualTo(aliceWallet.toString());
        assertThat(api.get(eve, "/api/v1/transactions/" + txId).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(api.get(eve, "/api/v1/wallets/" + aliceWallet).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cannotSpendFromSomeoneElsesWallet() {
        User alice = api.register();
        User eve = api.register();
        UUID aliceWallet = api.walletFor(alice, "USD");
        UUID eveWallet = api.walletFor(eve, "USD");
        api.deposit(alice, aliceWallet, 1_000);

        ResponseEntity<JsonNode> theft = api.transfer(eve, aliceWallet, eveWallet, 1_000);
        assertThat(theft.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(api.balance(alice, aliceWallet)).isEqualTo(1_000);
    }

    @Test
    void insufficientFundsIsRejectedAndChangesNothing() {
        User alice = api.register();
        User bob = api.register();
        UUID aliceWallet = api.walletFor(alice, "USD");
        UUID bobWallet = api.walletFor(bob, "USD");
        api.deposit(alice, aliceWallet, 100);

        ResponseEntity<JsonNode> response = api.transfer(alice, aliceWallet, bobWallet, 101);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("code").asText()).isEqualTo("INSUFFICIENT_FUNDS");

        assertThat(api.balance(alice, aliceWallet)).isEqualTo(100);
        assertThat(api.balance(bob, bobWallet)).isZero();
        assertThat(api.withdraw(alice, aliceWallet, 101).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void transfersBetweenDifferentCurrenciesAreRejected() {
        User alice = api.register();
        User bob = api.register();
        UUID usd = api.walletFor(alice, "USD");
        UUID inr = api.walletFor(bob, "INR");
        api.deposit(alice, usd, 1_000);

        ResponseEntity<JsonNode> response = api.transfer(alice, usd, inr, 100);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("code").asText()).isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void frozenWalletsCannotSendOrReceive() {
        User alice = api.register();
        User bob = api.register();
        UUID aliceWallet = api.walletFor(alice, "USD");
        UUID bobWallet = api.walletFor(bob, "USD");
        api.deposit(alice, aliceWallet, 1_000);

        assertThat(api.post(bob, "/api/v1/wallets/" + bobWallet + "/freeze", null).getBody().get("status").asText())
                .isEqualTo("FROZEN");
        ResponseEntity<JsonNode> toFrozen = api.transfer(alice, aliceWallet, bobWallet, 100);
        assertThat(toFrozen.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(toFrozen.getBody().get("code").asText()).isEqualTo("WALLET_FROZEN");

        api.post(bob, "/api/v1/wallets/" + bobWallet + "/unfreeze", null);
        assertThat(api.transfer(alice, aliceWallet, bobWallet, 100).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void invalidMoneyRequestsAreRejected() {
        User alice = api.register();
        UUID wallet = api.walletFor(alice, "USD");
        api.deposit(alice, wallet, 1_000);

        assertThat(api.transfer(alice, wallet, wallet, 10).getBody().get("code").asText())
                .isEqualTo("SAME_WALLET_TRANSFER");
        assertThat(api.deposit(alice, wallet, 0).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api.deposit(alice, wallet, -5).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(api.transfer(alice, wallet, UUID.randomUUID(), 10).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void walletRulesAreEnforced() {
        User alice = api.register();
        String accountId = api.post(alice, "/api/v1/accounts", Map.of("name", "Biz", "type", "BUSINESS"))
                .getBody().get("id").asText();
        String path = "/api/v1/accounts/" + accountId + "/wallets";

        assertThat(api.post(alice, path, Map.of("currency", "GBP")).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(api.post(alice, path, Map.of("currency", "gbp")).getBody().get("code").asText())
                .isEqualTo("WALLET_ALREADY_EXISTS");
        assertThat(api.post(alice, path, Map.of("currency", "XYZ")).getBody().get("code").asText())
                .isEqualTo("UNSUPPORTED_CURRENCY");
        assertThat(api.post(alice, "/api/v1/accounts", Map.of("name", "Sneaky", "type", "SYSTEM"))
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void moneyMovementIsAudited() {
        User alice = api.register();
        UUID wallet = api.walletFor(alice, "USD");
        api.deposit(alice, wallet, 1_000);

        JsonNode logs = api.get(alice, "/api/v1/audit-logs").getBody().get("content");
        assertThat(logs.findValuesAsText("action"))
                .contains("USER_REGISTERED", "ACCOUNT_CREATED", "WALLET_CREATED", "DEPOSIT_COMPLETED");
        JsonNode deposit = logs.get(0);
        assertThat(deposit.get("action").asText()).isEqualTo("DEPOSIT_COMPLETED");
        assertThat(deposit.get("details").get("amount").asLong()).isEqualTo(1_000);
    }
}
