package com.fitunity.auth.exception;

public class ReplayAttackException extends RuntimeException {
    public ReplayAttackException(String message) {
        super(message);
    }
}
