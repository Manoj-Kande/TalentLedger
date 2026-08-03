package com.talentledger.domain.shared;

/**
 * Functional result type that wraps either a success value or a failure error.
 *
 * <p>Eliminates exceptions for expected business rule violations. Forces callers
 * to handle both branches explicitly, similar to {@code Either} in functional
 * programming.
 *
 * <p>Usage:
 * <pre>
 *   Result&lt;Contact, ValidationError&gt; result = ContactFactory.create(...);
 *   if (result.isSuccess()) {
 *       contactRepository.save(result.getValue());
 *   } else {
 *       return errorResponse(result.getError());
 *   }
 * </pre>
 *
 * @param <T> the success value type
 * @param <E> the failure error type
 */
public final class Result<T, E> {

    private final T value;
    private final E error;
    private final boolean success;

    private Result(T value, E error, boolean success) {
        this.value = value;
        this.error = error;
        this.success = success;
    }

    /** Create a successful result. */
    public static <T, E> Result<T, E> success(T value) {
        return new Result<>(value, null, true);
    }

    /** Create a failure result. */
    public static <T, E> Result<T, E> failure(E error) {
        return new Result<>(null, error, false);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isFailure() {
        return !success;
    }

    /** Get the success value. Throws IllegalStateException if this is a failure. */
    public T getValue() {
        if (!success) {
            throw new IllegalStateException("Cannot call getValue() on a failure result");
        }
        return value;
    }

    /** Get the error. Throws IllegalStateException if this is a success. */
    public E getError() {
        if (success) {
            throw new IllegalStateException("Cannot call getError() on a success result");
        }
        return error;
    }

    /**
     * Map the success value to a new type. If this is a failure, propagate the error.
     */
    public <U> Result<U, E> map(java.util.function.Function<T, U> mapper) {
        if (success) {
            return Result.success(mapper.apply(value));
        }
        return Result.failure(error);
    }

    @Override
    public String toString() {
        if (success) {
            return "Result{success, value=" + value + "}";
        }
        return "Result{failure, error=" + error + "}";
    }
}
