package io.github.org4j.otp;

/** The properties' prefix. */
public final class OtpConstants {

  /** Every property of this library starts with it. */
  public static final String PROPERTIES_PREFIX = "org4j.otp";

  static final String KEY_FORMAT = "%s:%s:{%s}:%s";
  static final String KEY_CODE = "code";
  static final String KEY_ATTEMPTS = "attempts";
  static final String KEY_SENDS = "sends";
  static final String KEY_LAST = "last";

  static final String HASH_ALGORITHM = "HmacSHA256";
  static final String HASH_CODE = "code:";
  static final String HASH_SUBJECT = "subject:";

  static final String ISSUE_SCRIPT = "otp/issue.lua";
  static final String VERIFY_SCRIPT = "otp/verify.lua";

  static final String METER_ISSUED = "org4j.otp.issued";
  static final String METER_VERIFIED = "org4j.otp.verified";
  static final String METER_REJECTED = "org4j.otp.rejected";
  static final String TAG_FLOW = "flow";
  static final String TAG_REASON = "reason";

  static final String LOG_UNAVAILABLE = "One-time codes unavailable: {}";
  static final String SECRET_MISSING =
      "Set org4j.otp.secret: codes are stored as HMACs keyed with it.";

  private OtpConstants() {}
}
