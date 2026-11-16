package io.temporal.demo.wealthmanagement.common;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Connection settings and task queue names, read from the same environment variables as the
 * Python worker (TEMPORAL_ADDRESS, TEMPORAL_NAMESPACE, TEMPORAL_TASK_QUEUE, ...). The cloud
 * script maps setcloudenv.sh's certificate and key paths to TEMPORAL_CERT_PATH and
 * TEMPORAL_KEY_PKCS8_PATH.
 */
public class ServerInfo {

    public static String getCertPath() {
        return getEnv("TEMPORAL_CERT_PATH", "");
    }

    public static String getKeyPath() {
        // A PKCS8 key first (Java's TLS needs one), then the generic setting
        String keyPath = System.getenv("TEMPORAL_KEY_PKCS8_PATH");
        return keyPath != null && !keyPath.isEmpty() ? keyPath : getEnv("TEMPORAL_KEY_PATH", "");
    }

    public static String getApiKey() {
        return getEnv("TEMPORAL_API_KEY", "");
    }

    public static String getNamespace() {
        return getEnv("TEMPORAL_NAMESPACE", "default");
    }

    public static String getAddress() {
        return getEnv("TEMPORAL_ADDRESS", "127.0.0.1:7233");
    }

    public static String getTaskqueue() {
        return getEnv("TEMPORAL_TASK_QUEUE", "Supervisor");
    }

    public static String getTaskqueueOpenAccount() {
        return getEnv("TEMPORAL_TASK_QUEUE_OPEN_ACCOUNT", getTaskqueue());
    }

    public static boolean useClaimCheck() {
        return isTrue(getEnv("USE_CLAIM_CHECK", "false"));
    }

    /**
     * Whether a flag's value means on. Accepts the values the Python components' str_to_bool
     * accepts (true, t, yes, y or 1, in any case), so every component agrees on whether payloads
     * are claim-checked. Boolean.parseBoolean accepted only "true": with USE_CLAIM_CHECK=1 the
     * Python components claim-checked payloads this worker couldn't read.
     */
    static boolean isTrue(String value) {
        if (value == null) {
            return false;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "t", "yes", "y", "1" -> true;
            default -> false;
        };
    }

    /** For startup messages, worded like the Python worker's: whether claim check is on, and which Redis it uses. */
    public static String claimCheckStatus() {
        if (!useClaimCheck()) {
            return "Claim check: off";
        }
        return "Claim check: ON (payloads stored in Redis at "
                + getEnv("REDIS_HOST", "localhost") + ":" + getEnv("REDIS_PORT", "6379") + ")";
    }

    /** For logging: never includes the API key itself. */
    public static Map<String, String> getServerInfo() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("address", getAddress());
        info.put("namespace", getNamespace());
        info.put("auth", !getApiKey().isEmpty() ? "API key" : !getCertPath().isEmpty() ? "mTLS" : "none");
        info.put("claimCheck", String.valueOf(useClaimCheck()));
        info.put("taskQueue", getTaskqueue());
        info.put("taskQueueOpenAccount", getTaskqueueOpenAccount());
        return info;
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isEmpty() ? value : defaultValue;
    }
}
