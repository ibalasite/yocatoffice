package com.sayyogames.yocat.wallet.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.shared.DomainEventPublisher;
import com.sayyogames.yocat.shared.InvalidAmountException;
import com.sayyogames.yocat.wallet.domain.Currency;
import com.sayyogames.yocat.wallet.domain.LedgerEntry;
import com.sayyogames.yocat.wallet.domain.Wallet;
import com.sayyogames.yocat.wallet.domain.WalletBalanceChanged;
import com.sayyogames.yocat.wallet.domain.WalletRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Application-layer unit test (RED) — {@link TopUpWalletUseCase}, a &lt;&lt;Command&gt;&gt; use case.
 *
 * <p>Spec: EDD §4.1 (credit, no balance check) + §4.5.4 covering table; test-plan §3.1 Application layer.
 * Ports are Mockito mocks. Traces PRD AC RQ-002 / US-WAL-001 (TC-UNIT-WAL-004-B02 legal bounds).</p>
 *
 * <p>RED: {@code TopUpWalletUseCase.execute} is an un-wired skeleton (orchestrate-usecases, GREEN).</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TopUpWalletUseCaseTest {

    private static final AccountId ACCOUNT = AccountId.of(1001L);

    @Mock
    private WalletRepository walletRepo;

    @Mock
    private DomainEventPublisher publisher;

    private TopUpWalletUseCase useCase() {
        return new TopUpWalletUseCase(walletRepo, publisher);
    }

    @Test
    @DisplayName("TC-UNIT-TOPUPWALLET-S01: credit 5000 to empty wallet → 5000, ledger appended, event published")
    void topup_success_credits_and_publishes() {
        Wallet wallet = Wallet.of(ACCOUNT, 0L, 0L);
        when(walletRepo.findForUpdate(ACCOUNT, Currency.CASH)).thenReturn(Optional.of(wallet));

        WalletView view = useCase().execute(new TopUpCommand(ACCOUNT, Currency.CASH, 5_000L, "topup"));

        assertThat(view.cash()).isEqualTo(5_000L);
        verify(walletRepo).save(any(Wallet.class));
        verify(walletRepo).appendLedger(any(LedgerEntry.class));
        verify(publisher).publish(any(WalletBalanceChanged.class));
    }

    @ParameterizedTest(name = "TC-UNIT-WAL-004-B02: legal boundary amount {0} is accepted")
    @ValueSource(longs = {1L, 10_000_000L})
    @DisplayName("TC-UNIT-WAL-004-B02: inclusive legal bounds [1, 10,000,000] accepted (no InvalidAmountException)")
    void topup_legal_boundary_amounts_accepted(long amount) {
        Wallet wallet = Wallet.of(ACCOUNT, 0L, 0L);
        when(walletRepo.findForUpdate(ACCOUNT, Currency.CASH)).thenReturn(Optional.of(wallet));

        WalletView view = useCase().execute(new TopUpCommand(ACCOUNT, Currency.CASH, amount, "topup"));

        assertThat(view.cash()).isEqualTo(amount);
        verify(walletRepo).appendLedger(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("TC-UNIT-TOPUPWALLET-B01: amount 0 below lower bound → InvalidAmountException (400), no write")
    void topup_zero_amount_rejected() {
        assertThatThrownBy(() -> useCase().execute(new TopUpCommand(ACCOUNT, Currency.CASH, 0L, "topup")))
                .isInstanceOf(InvalidAmountException.class);

        verify(walletRepo, never()).save(any(Wallet.class));
        verify(walletRepo, never()).appendLedger(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("TC-UNIT-TOPUPWALLET-B02: amount > 10,000,000 above upper bound → InvalidAmountException (400)")
    void topup_amount_above_max_rejected() {
        assertThatThrownBy(() -> useCase().execute(new TopUpCommand(ACCOUNT, Currency.GEMS, 10_000_001L, "topup")))
                .isInstanceOf(InvalidAmountException.class);

        verify(publisher, never()).publish(any());
    }
}
