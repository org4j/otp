package uz.khakimjanovich.otp4j;

import lombok.experimental.UtilityClass;

@UtilityClass
public class OtpConstants {

  public static final String PROPERTIES_PREFIX = "otp4j";

  public static final String KEY_FORMAT = "%s:%s:{%s}:%s";

  public static final String HASH_CODE = "code:";

  public static final String HASH_SUBJECT = "subject:";

  public static final String LOG_UNAVAILABLE = "One-time codes unavailable: {}";

  public static final String KEY_CODE = "code";
  public static final String KEY_ATTEMPTS = "attempts";
  public static final String KEY_SENDS = "sends";
  public static final String KEY_LAST = "last";

  public static final String METER_ISSUED = "otp4j.issued";
  public static final String METER_VERIFIED = "otp4j.verified";
  public static final String METER_REJECTED = "otp4j.rejected";
  public static final String TAG_FLOW = "flow";
  public static final String TAG_REASON = "reason";

  public static final String HASH_ALGORITHM = "HmacSHA256";
  public static final String ISSUE_SCRIPT = "otp/issue.lua";
  public static final String VERIFY_SCRIPT = "otp/verify.lua";
  public static final String SECRET_MISSING =
      "Set otp4j.secret: codes are stored as HMACs keyed with it.";
}
