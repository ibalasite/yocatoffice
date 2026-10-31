package com.sayyogames.yocat.entitlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sayyogames.yocat.entitlement.domain.Entitlement;
import com.sayyogames.yocat.entitlement.domain.EntitlementKind;
import com.sayyogames.yocat.entitlement.domain.EntitlementRepository;
import com.sayyogames.yocat.shared.AccountId;
import com.sayyogames.yocat.wallet.api.WalletService;
import com.sayyogames.yocat.wallet.application.WalletView;
import com.sayyogames.yocat.wallet.domain.Currency;
import com.sayyogames.yocat.wallet.domain.InsufficientBalanceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Application-layer unit test (RED) — {@link BuyEntitlementUseCase}, a cross-module &lt;&lt;Command&gt;&gt; use case.
 *
 * <p>Spec: EDD §4.5.4.3 (atomic debit+grant) / §4.5.4.4 (errors); test-plan §3.1 Application layer.
 * The wallet BC is consumed through its Public API ({@link WalletService}) as a mock; the
 * {@link EntitlementRepository} port is mocked. Traces PRD AC RQ-003 / US-ENT-001.</p>
 *
 * <p>RED: {@code BuyEntitlementUseCase.execute} is an un-wired skeleton (orchestrate-usecases, GREEN).</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BuyEntitlementUseCaseTest {

    private static final AccountId ACCOUNT = AccountId.of(1001L);

    @Mock
    private EntitlementRepository entRepo;

    @Mock
    private WalletService walletService;

    private BuyEntitlementUseCase useCase() {
        return new BuyEntitlementUseCase(entRepo, walletService);
    }

    @Test
    @DisplayName("TC-UNIT-BUYENT-S01: not owned + balance ok → debit wallet then grant, returns result")
    void buy_success_debits_then_grants() {
        when(entRepo.existsOwned(ACCOUNT, EntitlementKind.COSMETIC, "hat_01")).thenReturn(false);
        when(walletService.debit(eq(ACCOUNT), eq(Currency.CASH), anyLong(), anyString()))
                .thenReturn(new WalletView(18_750L, 310L));
        when(entRepo.save(any(Entitlement.class))).thenReturn(new Entitlement());

        BuyResult result = useCase().execute(
                new BuyCommand(ACCOUNT, EntitlementKind.COSMETIC, "hat_01", Currency.CASH, 1_250L));

        assertThat(result.wallet().cash()).isEqualTo(18_750L);
        verify(entRepo).existsOwned(ACCOUNT, EntitlementKind.COSMETIC, "hat_01");
        verify(walletService).debit(eq(ACCOUNT), eq(Currency.CASH), eq(1_250L), eq("buy_cosmetic"));
        verify(entRepo).save(any(Entitlement.class));
    }

    @Test
    @DisplayName("TC-UNIT-BUYENT-E01: already owns (kind,itemId) → AlreadyOwnedException (409), no debit, no grant")
    void buy_already_owned_conflicts_without_debit() {
        when(entRepo.existsOwned(ACCOUNT, EntitlementKind.DREAM, "beach_house")).thenReturn(true);

        assertThatThrownBy(() -> useCase().execute(
                new BuyCommand(ACCOUNT, EntitlementKind.DREAM, "beach_house", Currency.GEMS, 500L)))
                .isInstanceOf(AlreadyOwnedException.class);

        verify(walletService, never()).debit(any(), any(), anyLong(), anyString());
        verify(entRepo, never()).save(any(Entitlement.class));
    }

    @Test
    @DisplayName("TC-UNIT-ENT-004-B01: missing itemId → IllegalArgumentException (400), no debit, no grant")
    void buy_blank_item_id_rejected() {
        assertThatThrownBy(() -> useCase().execute(
                new BuyCommand(ACCOUNT, EntitlementKind.COSMETIC, "  ", Currency.CASH, 1_250L)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(walletService, never()).debit(any(), any(), anyLong(), anyString());
        verify(entRepo, never()).save(any(Entitlement.class));
    }

    @Test
    @DisplayName("TC-UNIT-BUYENT-E02: insufficient balance propagates from WalletService → 402, no grant")
    void buy_insufficient_balance_propagates_without_grant() {
        when(entRepo.existsOwned(ACCOUNT, EntitlementKind.SCENARIO, "winter_map")).thenReturn(false);
        when(walletService.debit(eq(ACCOUNT), eq(Currency.CASH), anyLong(), anyString()))
                .thenThrow(new InsufficientBalanceException(800L));

        assertThatThrownBy(() -> useCase().execute(
                new BuyCommand(ACCOUNT, EntitlementKind.SCENARIO, "winter_map", Currency.CASH, 1_250L)))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(entRepo, never()).save(any(Entitlement.class));
    }
}
