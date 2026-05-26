package com.hrms.domain.common.exception;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

public class AccountLockedException extends BusinessException {

    public AccountLockedException(LocalDateTime lockedUntil) {
        super("Account is locked until " + lockedUntil, HttpStatus.LOCKED, "ACCOUNT_LOCKED");
    }
}
