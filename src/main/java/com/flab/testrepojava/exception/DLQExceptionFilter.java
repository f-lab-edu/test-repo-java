package com.flab.testrepojava.exception;

import com.fasterxml.jackson.core.JsonParseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

public class DLQExceptionFilter {

    public static boolean isSystemException(Throwable e) {
        return e instanceof ConnectException ||
                e instanceof SocketTimeoutException ||
                e instanceof JsonParseException ||
                e instanceof NullPointerException ||
                e instanceof IllegalStateException;
    }

    public static boolean isBusinessException(Throwable e) {
        return e instanceof OutOfStockException;
    }
}
