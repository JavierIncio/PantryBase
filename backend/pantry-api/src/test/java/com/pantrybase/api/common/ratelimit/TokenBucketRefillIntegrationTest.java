package com.pantrybase.api.common.ratelimit;

import com.pantrybase.api.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the refill branch of the token bucket script.
 *
 * <p>The script takes the current time as an argument, so this drives it with a controlled
 * clock instead of sleeping: the HTTP level tests in {@link RateLimitFilterIntegrationTest}
 * assert an exact remaining count while burning through the whole capacity, which a real
 * refill would make flaky, and a sleep-based test here would only move that flake.</p>
 */
public class TokenBucketRefillIntegrationTest extends AbstractIntegrationTest {

    private static final long CAPACITY = 5;
    private static final long REFILL_INTERVAL = 1_000;
    private static final String BUCKET = "rate:script-test:POST:/api/auth/login";

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private DefaultRedisScript<List> restrictTokenBucketScript;

    @BeforeEach
    void clearBucket() {
        redisTemplate.delete(BUCKET);
    }

    @Test
    void aNewBucketStartsFull() {
        BucketState state = consumeAt(1_000);

        assertThat(state.allowed()).isTrue();
        assertThat(state.remaining()).isEqualTo(CAPACITY - 1);
    }

    @Test
    void exhaustingTheBucketDeniesTheRequest() {
        for (int request = 0; request < CAPACITY; request++) {
            assertThat(consumeAt(1_000).allowed()).isTrue();
        }

        BucketState denied = consumeAt(1_000);

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.remaining()).isZero();
    }

    @Test
    void tokensAreReplenishedAfterEachInterval() {
        for (int request = 0; request < CAPACITY; request++) {
            consumeAt(1_000);
        }
        assertThat(consumeAt(1_000).allowed()).isFalse();

        // Just short of an interval, nothing has been replenished yet.
        assertThat(consumeAt(1_999).allowed()).isFalse();

        assertThat(consumeAt(2_000).allowed()).isTrue();
    }

    @Test
    void elapsedIntervalsReplenishThatManyTokens() {
        for (int request = 0; request < CAPACITY; request++) {
            consumeAt(1_000);
        }

        // Three intervals elapsed, so three tokens came back.
        BucketState state = consumeAt(4_000);

        assertThat(state.allowed()).isTrue();
        assertThat(state.remaining()).isEqualTo(2);
    }

    @Test
    void replenishmentNeverExceedsCapacity() {
        consumeAt(1_000);

        // Far more intervals than the bucket can hold.
        BucketState state = consumeAt(60_000);

        assertThat(state.allowed()).isTrue();
        assertThat(state.remaining()).isEqualTo(CAPACITY - 1);
    }

    @Test
    void elapsedTimeIsMeasuredFromTheLastRefillNotTheLastRequest() {
        for (int request = 0; request < CAPACITY; request++) {
            consumeAt(1_000);
        }

        // A denied request must not reset the refill clock: 2_500 is one interval after
        // the 1_500 refill, so the token is back even though the bucket was just touched.
        assertThat(consumeAt(2_500).allowed()).isTrue();
    }

    private record BucketState(boolean allowed, long remaining) {}

    private BucketState consumeAt(long now) {
        List<?> result = redisTemplate.execute(
                restrictTokenBucketScript,
                List.of(BUCKET),
                String.valueOf(CAPACITY),
                String.valueOf(REFILL_INTERVAL),
                String.valueOf(now));

        return new BucketState(((Long) result.get(0)) == 1L, (Long) result.get(1));
    }
}
