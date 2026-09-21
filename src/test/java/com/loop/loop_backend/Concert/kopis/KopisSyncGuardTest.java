package com.loop.loop_backend.Concert.kopis;

import com.loop.loop_backend.Artist.repository.ArtistRepository;
import com.loop.loop_backend.Concert.repository.ConcertImportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// KOPIS 싱크는 한 번에 하나만 돌아야 한다 (동시에 돌면 KOPIS 요청이 두 배로 몰려 차단이 심해지고 후보가 중복될 수 있다).
// 스케줄러와 수동 실행이 겹치는 상황을 스레드 두 개로 재현해 검증한다.
class KopisSyncGuardTest {

    private KopisClient kopisClient;
    private KopisSyncService service;

    @BeforeEach
    void setUp() {
        ArtistRepository artistRepository = mock(ArtistRepository.class);
        kopisClient = mock(KopisClient.class);
        service = new KopisSyncService(artistRepository, mock(ConcertImportRepository.class), kopisClient);
        when(artistRepository.findByAutoFetchConcertsTrue()).thenReturn(List.of());
    }

    /** 싱크가 KOPIS 수집 단계에서 release를 기다리며 멈춰 있도록 만든다. started는 멈춘 시점에 카운트다운된다. */
    private void givenSyncBlockedUntil(CountDownLatch started, CountDownLatch release) {
        when(kopisClient.getAllUpcomingPerformances()).thenAnswer(invocation -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return List.of();
        });
    }

    @Test
    void 싱크가_돌고_있는_동안에는_실행_중으로_표시된다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        givenSyncBlockedUntil(started, release);

        assertThat(service.isRunning()).isFalse();
        Thread running = new Thread(service::syncAll);
        running.start();
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

        assertThat(service.isRunning()).isTrue();

        release.countDown();
        running.join(2000);
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void 이미_싱크가_돌고_있으면_새로_시작하지_않고_건너뛴다() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        givenSyncBlockedUntil(started, release);
        Thread running = new Thread(service::syncAll);
        running.start();
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

        service.syncAll(); // 겹쳐서 호출 - 기다리지 않고 바로 돌아와야 한다

        release.countDown();
        running.join(2000);
        // KOPIS 수집은 처음 실행 한 번뿐
        verify(kopisClient, times(1)).getAllUpcomingPerformances();
    }

    @Test
    void 싱크가_끝나면_다음_싱크를_실행할_수_있다() {
        when(kopisClient.getAllUpcomingPerformances()).thenReturn(List.of());

        service.syncAll();
        service.syncAll();

        verify(kopisClient, times(2)).getAllUpcomingPerformances();
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void 싱크_도중_예외가_나도_실행_중_표시가_풀려_다음_싱크를_막지_않는다() {
        when(kopisClient.getAllUpcomingPerformances())
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(List.of());

        assertThatThrownBy(service::syncAll).isInstanceOf(RuntimeException.class);
        assertThat(service.isRunning()).isFalse();

        service.syncAll();
        verify(kopisClient, times(2)).getAllUpcomingPerformances();
    }
}