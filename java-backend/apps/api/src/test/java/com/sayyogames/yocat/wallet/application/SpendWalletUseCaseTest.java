package com.sayyogames.yocat.wallet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.shared.DomainEventPublisher;
import com.sayyogames.yocat.shared.InvalidAmountException;
import com.sayyogames.yocat.wallet.domain.Currency;
import com.sayyogames.yocat.wallet.domain.InsufficientBalanceException;
import com.sayyogames.yocat.wallet.domain.LedgerEntry;
import com.sayyogames.yocat.wallet.domain.Wallet;
import com.sayyogames.yocat.wallet.domain.WalletBalanceChanged;
import com.sayyogames.yocat.wallet.domain.WalletRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Application-layer unit test (RED) — {@link SpendWalletUseCase}, a &lt;&lt;Command&gt;&gt; use case.
 *
 * <p>Spec: EDD §4.7 Alg 1 (Atomic Debit) + §4.5.4.1/§4.5.4.2 sequence; test-plan §3.1 Application layer
 * (mock all ports, no DB). Ports ({@link WalletRepository}, {@link DomainEventPublisher}) are Mockito mocks.
 * Traces PRD AC RQ-002 / US-WAL-001.</p>
 *
 * <p>RED: {@code SpendWalletUseCase.execute} is an un-wired skeleton (orchestrate-usecases, GREEN), so every
 * expectation below currently fails — this is the intended failing state before implementation.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SpendWalletUseCaseTest {

    private static final AccountId ACCOUNT = AccountId.of(1001L);

    @Mock
    private WalletRepository walletRepo;

    @Mock
    private DomainEventPublisher publisher;

    private SpendWalletUseCase useCase() {
        return new SpendWalletUseCase(walletRepo, publisher);
    }

    @Test
    @DisplayName("TC-UNIT-SPENDWALLET-S01: balance 20000 − 1250 → 18750, ledger appended, event published")
    void spend_success_debits_and_publishes() {
        Wallet wallet = Wallet.of(ACCOUNT, 20_000L, 310L);
        when(walletRepo.findForUpdate(ACCOUNT, Currency.CASH)).thenReturn(Optional.of(wallet));

        WalletView view = useCase().execute(new SpendCommand(ACCOUNT, Currency.CASH, 1_250L, "buy_cosmetic"));

        assertThat(view.cash()).isEqualTo(18_750L);
        verify(walletRepo).findForUpdate(ACCOUNT, Currency.CASH);
        verify(walletRepo).save(any(Wallet.class));
        verify(walletRepo).appendLedger(any(LedgerEntry.class));
        verify(publisher).publish(any(WalletBalanceChanged.class));
    }

    @Test
    @DisplayName("TC-UNIT-SPENDWALLET-E01: balance 800 < 1250 → InsufficientBalanceException (402), no write, no event")
    void spend_insufficient_balance_rolls_back_without_ledger() {
        Wallet wallet = Wallet.of(ACCOUNT, 800L, 310L);
        when(walletRepo.findForUpdate(ACCOUNT, Currency.CASH)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> useCase().execute(new SpendCommand(ACCOUNT, Currency.CASH, 1_250L, "buy_cosmetic")))
                .isInstanceOf(InsufficientBalanceException.class);

        // 402 path writes no ledger and publishes no balance-changed event (EDD §4.5.4.2).
        verify(walletRepo, never()).save(any(Wallet.class));
        verify(walletRepo, never()).appendLedger(any(LedgerEntry.class));
        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("TC-UNIT-SPENDWALLET-B01: amount ≤ 0 → InvalidAmountException (400) before acquiring the row lock")
    void spend_non_positive_amount_rejected_fast() {
        assertThatThrownBy(() -> useCase().execute(new SpendCommand(ACCOUNT, Currency.CASH, 0L, "buy_cosmetic")))
                .isInstanceOf(InvalidAmountException.class);

        // Guard runs before the lock, so the repository is never touched (EDD §4.7 Alg 1).
        verify(walletRepo, never()).findForUpdate(eq(ACCOUNT), any(Currency.class));
        verify(walletRepo, never()).save(any(Wallet.class));
    }

    @Test
    @DisplayName("TC-UNIT-SPENDWALLET-B02: amount > 10,000,000 upper bound → InvalidAmountException (400)")
    void spend_amount_above_max_rejected() {
        assertThatThrownBy(() -> useCase().execute(new SpendCommand(ACCOUNT, Currency.CASH, 10_000_001L, "buy_dream")))
                .isInstanceOf(InvalidAmountException.class);

        verify(walletRepo, never()).appendLedger(any(LedgerEntry.class));
    }
}
