package io.github.org4j.otp;

import lombok.experimental.UtilityClass;

@UtilityClass
public class OtpConstants {

  public static final String PROPERTIES_PREFIX = "org4j.otp";

  public static final String KEY_FORMAT = "%s:%s:{%s}:%s";

  public static final String HASH_CODE = "code:";

  public static final String HASH_SUBJECT = "subject:";

  public static final String LOG_UNAVAILABLE = "One-time codes unavailable: {}";

  public static final String KEY_CODE = "code";
  public static final String KEY_ATTEMPTS = "attempts";
  public static final String KEY_SENDS = "sends";
  public static final String KEY_LAST = "last";

  public static final String METER_ISSUED = "org4j.otp.issued";
  public static final String METER_VERIFIED = "org4j.otp.verified";
  public static final String METER_REJECTED = "org4j.otp.rejected";
  public static final String TAG_FLOW = "flow";
  public static final String TAG_REASON = "reason";

  public static final String HASH_ALGORITHM = "HmacSHA256";
  public static final String ISSUE_SCRIPT = "otp/issue.lua";
  public static final String VERIFY_SCRIPT = "otp/verify.lua";
  public static final String SECRET_MISSING =
      "Set org4j.otp.secret: codes are stored as HMACs keyed with it.";
}
