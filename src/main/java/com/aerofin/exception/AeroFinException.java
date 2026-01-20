package com.aerofin.exception;

import lombok.Getter;

/**
 * Aero-Fin 业务异常基类
 *
 * @author Aero-Fin Team
 */
@Getter
public class AeroFinException extends RuntimeException {

    private final String errorCode;

    public AeroFinException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AeroFinException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
