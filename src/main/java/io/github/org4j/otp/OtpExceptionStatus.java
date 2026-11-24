package io.github.org4j.otp;

import io.github.org4j.error.StatusThrowable;

/** Why a code was not issued or not accepted; each one's HTTP status is in the README. */
public enum OtpExceptionStatus implements StatusThrowable {
  /** The code expired, was already used, or was never sent. */
  OTP_EXPIRED,
  /** The code is wrong. */
  OTP_INVALID,
  /** Too many wrong codes in this send window; carries Retry-After. */
  OTP_ATTEMPTS_EXCEEDED,
  /** Too many codes sent in this send window; carries Retry-After. */
  OTP_SEND_LIMIT,
  /** A code was sent less than {@code resend-after} ago; carries Retry-After. */
  OTP_RESEND_TOO_EARLY,
  /** Valkey can't be reached, so nothing is issued or accepted. */
  OTP_UNAVAILABLE;

  @Override
  public String getType() {
    return name();
  }
}
