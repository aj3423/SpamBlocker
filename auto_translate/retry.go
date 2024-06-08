package main

import (
	"errors"
	"fmt"
)

type RetryableError struct {
	Err error
}

func (e *RetryableError) Error() string {
	return e.Err.Error()
}

var retryableErr *RetryableError

// ---- global func ----

func Retryable(e error) error {
	return &RetryableError{Err: e}
}

func IsRetryable(e error) bool {
	return errors.As(e, &retryableErr)
}

type MaxRetryReached struct {
	TriedTimes int
	Err        error
}

var maxRetryReachedErr *MaxRetryReached

func (e *MaxRetryReached) Error() string {
	return fmt.Sprintf(`exceeded retry limit: %d times, error: %s`, e.TriedTimes, e.Err.Error())
}

func ReachedMaxRetry(e error) bool {
	return errors.As(e, &maxRetryReachedErr)
}

type Func func(attempt int) (err error)

/*
usage:

	e :=retry.Do(10, func(attempt int ) error {
		if e == network_error {
			return Retryable(e)
		}
		return e
	})
*/
func Retry(maxAttempt int, fn Func) error {
	attempt := 0
	var e error

	for {
		e = fn(attempt)
		if e == nil || !IsRetryable(e) {
			break
		}
		attempt++
		if attempt >= maxAttempt {
			return &MaxRetryReached{
				TriedTimes: maxAttempt,
				Err:        e,
			}
		}
	}
	return e
}
