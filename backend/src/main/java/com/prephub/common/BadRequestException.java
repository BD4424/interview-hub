package com.prephub.common;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String msg) { super(msg); }
}
