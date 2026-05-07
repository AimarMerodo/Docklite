package es.docklite.docklitebackend.auth.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks failed login attempts in memory and locks accounts temporarily
 * after too many consecutive failures, mitigating brute-force attacks.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_TIME_MILLIS = 15L * 60L * 1000L; // 15 minutes

    private final ConcurrentMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    public boolean isLocked(String key) {
        AttemptRecord rec = attempts.get(normalize(key));
        if (rec == null) return false;
        if (rec.count < MAX_ATTEMPTS) return false;
        long elapsed = Instant.now().toEpochMilli() - rec.lastFailureMs;
        if (elapsed < LOCK_TIME_MILLIS) return true;
        // lock expired
        attempts.remove(normalize(key));
        return false;
    }

    public void recordFailure(String key) {
        attempts.compute(normalize(key), (k, rec) -> {
            long now = Instant.now().toEpochMilli();
            if (rec == null) return new AttemptRecord(1, now);
            return new AttemptRecord(rec.count + 1, now);
        });
    }

    public void recordSuccess(String key) {
        attempts.remove(normalize(key));
    }

    private String normalize(String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }

    private record AttemptRecord(int count, long lastFailureMs) {}
}
