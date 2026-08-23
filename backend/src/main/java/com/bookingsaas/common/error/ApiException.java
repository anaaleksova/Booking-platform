package com.bookingsaas.common.error;

/**
 * Base class for every error the domain raises deliberately.
 *
 * <p>Carrying an {@link ErrorCode} means the HTTP status is decided by the
 * domain once, rather than re-derived in each controller.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApiException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    // --- shorthand factories used across the modules ----------------------

    public static ApiException notFound(String entity, Object id) {
        return new ApiException(ErrorCode.NOT_FOUND, entity + " " + id + " was not found");
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.NOT_FOUND, message);
    }

    public static ApiException of(ErrorCode code, String message) {
        return new ApiException(code, message);
    }

    public static ApiException accessDenied(String message) {
        return new ApiException(ErrorCode.ACCESS_DENIED, message);
    }

    public static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message);
    }
}
