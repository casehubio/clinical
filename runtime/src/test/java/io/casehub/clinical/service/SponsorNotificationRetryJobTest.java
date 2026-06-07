package io.casehub.clinical.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SponsorNotificationRetryJobTest {

    @Mock SponsorNotificationStore store;
    @Mock SponsorNotificationDeliveryService delivery;
    @Mock Clock clock;
    @InjectMocks SponsorNotificationRetryJob job;

    private static final UUID ID_1 = UUID.randomUUID();
    private static final UUID ID_2 = UUID.randomUUID();
    private static final UUID ID_3 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        job.batchSize = 100;
        when(clock.instant()).thenReturn(Instant.parse("2026-06-07T10:00:00Z"));
    }

    @Test
    void tick_continues_after_one_delivery_throws() {
        when(store.findEligibleIds(any(Instant.class), anyInt())).thenReturn(List.of(ID_1, ID_2, ID_3));
        // lenient: ID_2-specific stub; Mockito strict mode would complain about calls with ID_1/ID_3
        lenient().doThrow(new RuntimeException("connector timeout")).when(delivery).attemptDelivery(ID_2);

        job.tick();

        verify(delivery).attemptDelivery(ID_1);
        verify(delivery).attemptDelivery(ID_2);
        verify(delivery).attemptDelivery(ID_3);
    }

    @Test
    void tick_calls_all_eligible_notifications() {
        when(store.findEligibleIds(any(Instant.class), anyInt())).thenReturn(List.of(ID_1, ID_2, ID_3));

        job.tick();

        verify(delivery, times(3)).attemptDelivery(any(UUID.class));
    }

    @Test
    void tick_does_nothing_when_no_eligible_notifications() {
        when(store.findEligibleIds(any(Instant.class), anyInt())).thenReturn(List.of());

        job.tick();

        verify(delivery, times(0)).attemptDelivery(any());
    }
}
