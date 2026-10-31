package com.sayyogames.yocat.member.application;

/**
 * Raised when the registration email is already taken (EDD §4.5.4.5 / §5.4 → HTTP 409).
 *
 * <p>Enforces the {@code member.accounts.email} global-unique rule at the application layer so no
 * account is created and no {@code MemberRegistered} event is published for a duplicate email.</p>
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException() {
        super("email 已註冊");
    }
}
