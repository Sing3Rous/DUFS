package com.dufs.exceptions;

public class DufsException extends Exception {
    public DufsException(String errorMessage) {
        super(errorMessage);
    }
    public DufsException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }
}
