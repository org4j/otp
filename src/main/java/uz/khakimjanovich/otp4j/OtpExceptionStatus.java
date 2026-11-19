package uz.khakimjanovich.otp4j;

import uz.khakimjanovich.error4j.StatusThrowable;

public enum OtpExceptionStatus implements StatusThrowable {
  OTP_EXPIRED,
  OTP_INVALID,
  OTP_ATTEMPTS_EXCEEDED,
  OTP_SEND_LIMIT,
  OTP_RESEND_TOO_EARLY,
  OTP_UNAVAILABLE;

  @Override
  public String getType() {
    return name();
  }
}
