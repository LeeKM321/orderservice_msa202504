package com.playdata.orderingservice.ordering.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.playdata.orderingservice.common.auth.TokenUserInfo;
import com.playdata.orderingservice.ordering.dto.OrderNotificationEvent;
import com.playdata.orderingservice.ordering.dto.OrderingListResDto;
import com.playdata.orderingservice.ordering.entity.Ordering;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SseController {

    private final RabbitTemplate rabbitTemplate;
    // Map 완전 제거 -> 서버 자체가 여러 개로 늘어나면 Map도 답이 없다...

    @GetMapping("/subscribe")
    public SseEmitter subscribe(@AuthenticationPrincipal TokenUserInfo userInfo) {
        // 알림 서비스 구현 핵심 객체
        SseEmitter emitter = new SseEmitter(24 * 60 * 60 * 1000L);
        log.info("Subscribing to {}", userInfo.getEmail());

        try {
            // 연결 성공 메세지 전송
            emitter.send(
                    SseEmitter.event()
                            .name("connect")
                            .data("connected!!")
            );

            // 큐에 쌓인 모든 메시지를 즉시 전송
            consumeQueuedMessages(emitter, userInfo.getEmail());

            // 실시간 알림을 위한 동적 리스너 등록
            startRealtimeListener(emitter, userInfo.getEmail());

            // 하트비트 설정
            setupHeartbeat(emitter);


        } catch (IOException e) {
            e.printStackTrace();
        }

        return emitter;

    }

    private void consumeQueuedMessages(SseEmitter emitter, String email) {

    }

    // RabbitMQ를 지속적으로 감시해서 새 메세지가 생기면 SSE로 전송하는 역할 수행
    private void startRealtimeListener(SseEmitter emitter, String email) {
        // 비동기 처리 과정에서 멀티스레드 환경에 맞게 설계된 boolean 타입 전용 클래스
        AtomicBoolean isConnected = new AtomicBoolean(true);

        // onCompletion: emitter 객체의 지속 시간이 만료되었을 때 실행되는 메서드.
        // onCompletion이 호출되었다는 건 연결이 끊김을 의미. -> isConnected를 false
        emitter.onCompletion(() -> {
            isConnected.set(false);
            log.info("sse 연결 만료됨!");
        });

        // 별도의 스레드에서 비동기적으로 실행될 것이다. -> 지속적으로 rabbitmq를 감시
        CompletableFuture.runAsync(() -> {
            log.info("실시간 리스너 시작: {}", email);

            // emitter가 살아있는 동안은 계속 동작해라.
            while (isConnected.get()) {
                try {
                    // 새로운 메세지 대기 (타임아웃: 5초)
                    Message message
                            = rabbitTemplate.receive("admin.order.notifications", 5000);

                    if (message != null) {
                        // 메시지가 도착했다면 (null이 아니라면)
                        String json = new String(message.getBody(), StandardCharsets.UTF_8);
                        ObjectMapper objectMapper = new ObjectMapper();
                        objectMapper.registerModule(new JavaTimeModule());
                        OrderNotificationEvent event
                                = objectMapper.readValue(json, OrderNotificationEvent.class);

                        emitter.send(SseEmitter.event()
                                .name("new-order")
                                .data(event));

                    }
                } catch (Exception e) {
                    log.error("실시간 리스너 오류: {}", email, e);
                }
            }

            log.info("실시간 리스너 종료: {}", email);
        });
    }

    private void setupHeartbeat(SseEmitter emitter) {
        ScheduledExecutorService scheduler
                = Executors.newScheduledThreadPool(1);

        // 30초마다 heartbeat 메시지를 전송해서 연결 유지
        // 클라이언트에서 사용하는 EventSourcePolyfill이 45초동안 활동이 없으면 지맘대로 연결 종료.
        scheduler.scheduleAtFixedRate(() -> {
            // 일정하게 동작시킬 로직을 작성
            try {
                // heartbeat 전송 전에 클라이언트 상태 파악.
                emitter.onCompletion(() -> {
                    scheduler.shutdown();
                });
                emitter.onTimeout(() -> {
                    scheduler.shutdown();
                });
                emitter.onError((throwable) -> {
                    scheduler.shutdown();
                });

                emitter.send(
                        SseEmitter.event()
                                .name("heartbeat")
                                .data("keep-alive") // 클라이언트 단이 살아있는지 확인
                );
            } catch (IOException e) {
                e.printStackTrace();
                log.info("Failed to send heartbeat");
                emitter.complete();
                scheduler.shutdown();
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

}









