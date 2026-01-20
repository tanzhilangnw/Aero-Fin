package com.aerofin.exception;

/**
 * 向量存储异常
 *
 * @author Aero-Fin Team
 */
public class VectorStoreException extends AeroFinException {

    public VectorStoreException(String message) {
        super("VECTOR_STORE_ERROR", message);
    }

    public VectorStoreException(String message, Throwable cause) {
        super("VECTOR_STORE_ERROR", message, cause);
    }
}
