package com.sayyogames.yocat.member.domain;

import com.sayyogames.yocat.shared.AccountId;

/**
 * &lt;&lt;AggregateRoot&gt;&gt; — member BC identity authority (EDD §4.5.2 class-domain,
 * Account Invariant Table). Pure domain — no Spring, no JDBC.
 *
 * <p>Holds the stored password credential ({@code saltHex$hashHex}) and verifies a raw password
 * against it in constant time (EDD §4.7 Algorithm 3, PRD §5.1 AC-5). The actual hashing is delegated
 * to the {@link PasswordHasher} port (Dependency Inversion): the domain default is the self-contained
 * {@link Sha256PasswordHasher}, and the infrastructure {@code ScryptPasswordEncoder} is installed in
 * production via {@link #usePasswordHasher(PasswordHasher)}.
 */
public class Account {

    private static volatile PasswordHasher passwordHasher = new Sha256PasswordHasher();

    private AccountId id;
    private Email email;
    private DisplayName displayName;
    private String passwordHash;

    protected Account() {
    }

    private Account(AccountId id, Email email, DisplayName displayName, String passwordHash) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
    }

    /**
     * Installs the password hashing strategy for the member domain (Dependency Inversion seam).
     * Production wires the infrastructure {@code ScryptPasswordEncoder} (EDD §4.7 Algorithm 3);
     * absent that, the self-contained {@link Sha256PasswordHasher} default applies.
     */
    public static void usePasswordHasher(PasswordHasher hasher) {
        if (hasher == null) {
            throw new IllegalArgumentException("passwordHasher 不可為 null");
        }
        passwordHasher = hasher;
    }

    /** Registers a new, not-yet-persisted account (id assigned by the repository on save). */
    public static Account register(Email email, DisplayName displayName, String passwordHash) {
        return new Account(null, email, displayName, passwordHash);
    }

    /** Reconstitutes a persisted account (repository rehydration). */
    public static Account of(AccountId id, Email email, DisplayName displayName, String passwordHash) {
        return new Account(id, email, displayName, passwordHash);
    }

    public AccountId id() {
        return id;
    }

    public Email email() {
        return email;
    }

    public DisplayName displayName() {
        return displayName;
    }

    /**
     * The stored password credential ({@code saltHex$hashHex}, never plaintext) for repository persistence
     * (SCHEMA {@code member.accounts.pass_hash}). Exposed for the infrastructure adapter to round-trip the
     * digest on save/rehydrate; it is a one-way hash, not the raw password.
     */
    public String passwordHash() {
        return passwordHash;
    }

    /**
     * True when {@code raw} matches the stored password credential, compared in constant time via the
     * configured {@link PasswordHasher} (EDD §4.7 Algorithm 3; PRD §5.1 AC-5 timingSafeEqual).
     */
    public boolean verifyPassword(String raw) {
        return passwordHasher.matches(raw, passwordHash);
    }
}
