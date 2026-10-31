package com.sayyogames.yocat.wallet.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.shared.DomainEventPublisher;
import com.sayyogames.yocat.wallet.application.WalletView;
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
 * Application-layer unit test (RED) — {@link WalletService}, the wallet BC &lt;&lt;ApplicationService&gt;&gt; Public API.
 *
 * <p>Spec: EDD §4.1 (Public NamedInterface) + §4.7 Alg 1; test-plan §3.1 Application layer. Ports
 * ({@link WalletRepository}, {@link DomainEventPublisher}) are mocked, no DB. Traces PRD AC RQ-002 / US-WAL-001.</p>
 *
 * <p>Covers the CQRS split explicitly: {@code topUp}/{@code debit} are Commands (writes + event), while
 * {@code findWallet} is a Query that must have no side effects (no save, no ledger, no event).</p>
 *
 * <p>RED: {@code WalletService} is an un-wired skeleton (orchestrate-usecases, GREEN).</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletServiceTest {

    private static final AccountId ACCOUNT = AccountId.of(1001L);

    @Mock
    private WalletRepository walletRepo;

    @Mock
    private DomainEventPublisher publisher;

    private WalletService service() {
        return new WalletService(walletRepo, publisher);
    }

    @Test
    @DisplayName("TC-UNIT-WALLETSVC-S01: topUp credits balance, appends ledger and publishes event (Command)")
    void topUp_command_credits_and_publishes() {
        Wallet wallet = Wallet.of(ACCOUNT, 1_000L, 0L);
        when(walletRepo.findForUpdate(ACCOUNT, Currency.CASH)).thenReturn(Optional.of(wallet));

        WalletView view = service().topUp(ACCOUNT, Currency.CASH, 5_000L, "topup");

        assertThat(view.cash()).isEqualTo(6_000L);
        verify(walletRepo).save(any(Wallet.class));
        verify(walletRepo).appendLedger(any(LedgerEntry.class));
        verify(publisher).publish(any(WalletBalanceChanged.class));
    }

    @Test
    @DisplayName("TC-UNIT-WALLETSVC-S02: debit 20000 − 1250 → 18750, ledger appended, event published (Command)")
    void debit_command_debits_and_publishes() {
        Wallet wallet = Wallet.of(ACCOUNT, 20_000L, 310L);
        when(walletRepo.findForUpdate(ACCOUNT, Currency.CASH)).thenReturn(Optional.of(wallet));

        WalletView view = service().debit(ACCOUNT, Currency.CASH, 1_250L, "buy_cosmetic");

        assertThat(view.cash()).isEqualTo(18_750L);
        verify(walletRepo).save(any(Wallet.class));
        verify(walletRepo).appendLedger(any(LedgerEntry.class));
        verify(publisher).publish(any(WalletBalanceChanged.class));
    }

    @Test
    @DisplayName("TC-UNIT-WALLETSVC-E01: debit with balance 800 < 1250 → InsufficientBalanceException (402), no write")
    void debit_insufficient_balance_rejected() {
        Wallet wallet = Wallet.of(ACCOUNT, 800L, 310L);
        when(walletRepo.findForUpdate(ACCOUNT, Currency.CASH)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service().debit(ACCOUNT, Currency.CASH, 1_250L, "buy_cosmetic"))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(walletRepo, never()).save(any(Wallet.class));
        verify(walletRepo, never()).appendLedger(any(LedgerEntry.class));
        verify(publisher, never()).publish(any());
    }

    @Test
    @DisplayName("TC-UNIT-WALLETSVC-Q01: findWallet is a CQRS Query — reads only, no save / ledger / event")
    void findWallet_query_has_no_side_effects() {
        Wallet wallet = Wallet.of(ACCOUNT, 18_750L, 310L);
        when(walletRepo.findByAccount(ACCOUNT)).thenReturn(Optional.of(wallet));

        WalletView view = service().findWallet(ACCOUNT);

        assertThat(view.cash()).isEqualTo(18_750L);
        assertThat(view.gems()).isEqualTo(310L);
        // Query must not mutate state or emit events (EDD §4.5.2 CQRS; template Review #3).
        verify(walletRepo, never()).save(any(Wallet.class));
        verify(walletRepo, never()).appendLedger(any(LedgerEntry.class));
        verify(publisher, never()).publish(any());
    }
}
