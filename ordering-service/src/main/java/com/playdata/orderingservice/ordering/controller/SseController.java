package com.playdata.orderingservice.ordering.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.playdata.orderingservice.common.auth.TokenUserInfo;
import com.playdata.orderingservice.ordering.dto.OrderNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SseController {

    // 현재 인스턴스의 활성 연결들만 저장
    /*
    Ordering-service 인스턴스가 여러개 증가할 가능성이 있겠죠?
    인스턴스1 -> 관리자A, 관리자B
    인스턴스2 -> 관리자C
    인스턴스3 -> 관리자D, 관리자E

    새 주문 -> RabbitMQ Queue -> 인스턴스 1,2,3는 동시에 동일한 메시지 수신. -> 관리자에게 알림 전송 가능
    각 인스턴스는 자기와 연결된 관리자만 신경쓰면 됨. -> 관리자가 여러명일 수 있으니 그들을 Map으로 관리
     */
    private final Map<String, SseEmitter> activeConnections
            = new ConcurrentHashMap<>();

    private final RabbitTemplate rabbitTemplate;

    @GetMapping("/subscribe")
    public SseEmitter subscribe(@AuthenticationPrincipal TokenUserInfo userInfo) {
        String userEmail = userInfo.getEmail();

        // 매우 긴 타임아웃 설정 (5시간) - EventSourcePolyfill이 알아서 재연결함
        SseEmitter emitter = new SseEmitter(0L);

        log.info("SSE 구독 시작: {}", userEmail);

        try {
            // 기존 연결이 있다면 정리
            SseEmitter oldEmitter = activeConnections.put(userEmail, emitter);
            if (oldEmitter != null) {
                try {
                    oldEmitter.complete();
                } catch (Exception e) {
                    // 무시
                }
            }

            // 연결 확인 메시지만 전송
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("SSE connected"));

            // 로그인 시 대기중인 알림들 한번에 전송
            sendPendingNotifications(emitter, userEmail);

            // 연결 종료 시 정리 - 매우 간단하게
            emitter.onCompletion(() -> {
                activeConnections.remove(userEmail);
                log.info("SSE 연결 정상 종료: {}", userEmail);
            });

            emitter.onTimeout(() -> {
                activeConnections.remove(userEmail);
                log.info("SSE 연결 타임아웃: {}", userEmail);
            });

            emitter.onError((ex) -> {
                activeConnections.remove(userEmail);
                // Broken pipe -> 연결이 실패했거나, 연결이 중간에 끊겼을 때 발생하는 예외
                if (ex.getMessage() != null && ex.getMessage().contains("Broken pipe")) {
                    log.debug("SSE 연결 끊김 (클라이언트 종료): {}", userEmail);
                } else {
                    log.info("SSE 연결 오류: {} - {}", userEmail, ex.getMessage());
                }
            });

        } catch (Exception e) {
            log.error("SSE 초기화 실패: {}", userEmail, e);
            activeConnections.remove(userEmail);
        }

        return emitter;
    }

    private void sendPendingNotifications(SseEmitter emitter, String userEmail) {
        try {
            int count = 0;
            Object message;

            while (
                    // pending 큐에서 메세지를 하나 가져왔을 때 결과가 null이 아니라면 반복문 실행, null이면 반복문 종료.
                    (message = rabbitTemplate.receiveAndConvert("admin.pending.notifications")) != null) {
                if (message instanceof OrderNotificationEvent) {
                    emitter.send(SseEmitter.event()
                            .name("pending-order")
                            .data(message));
                    count++;
                }
                // 너무 많으면 제한
                if (count >= 100) break;
            }

            if (count > 0) {
                log.info("관리자 {}, 대기중인 {}개 주문 알림 발송.", userEmail, count);
            } else {
                log.info("대기중인 알림 없음!");
            }

        } catch (Exception e) {
            log.error("대기 중인 알림 전송 실패: {}", userEmail, e);
        }

    }

    /*
    이전에는 emitter를 하나만 생성했고, Map이 없어서 따로 보관을 못했습니다.
    그래서 큐에 메시지 들어오면 수신하는 메서드를 직접 호출하는 방식을 쓸 수밖에 없어서 RabbitListener를 못썼음.
    지금은 우리가 emitter 객체를 Map에 저장해 놓고 언제든 꺼내서 쓸 수 있기 때문에
    @RabbitListener를 사용할 수 있다. -> admin.order.notifications 큐에 메세지 발신되면 자동 호출
     */
    @RabbitListener(queues = "admin.order.notifications")
    public void handleOrderNotification(OrderNotificationEvent event) {
        // json 문자열을 직접 DTO로 변환할 필요 없고, 매개값으로 선언해서 받으세요.
        // RabbitListener가 변환 해줍니다 -> Listener가 converter를 내장하고 있어요.

        // 활성화된 sse 연결이 없으면 즉시 종료
        if (activeConnections.isEmpty()) {
            rabbitTemplate.convertAndSend("admin.pending.notifications", event);
            log.info("활성 관리자 없음 - 대기 큐로 전송, 주문: {}", event.getOrderId());
            return;
        }

        try {
            log.info("수신된 메시지 객체: {}", event);
            log.info("새 주문 알림 - 활성 관리자: {}", activeConnections.size());

            // 안전하게 메시지 전송
            /*
            entrySet(): Map에 있는 데이터를 Entry(사용자이메일 + emitter객체)타입으로 묶어서 하나씩 처리
            removeIf(): true를 반환하면 -> 해당 entry를 Map에서 제거
                        false를 반환하면 -> 해당 entry를 Map에 유지
            Map에 있는 Entry를 하나씩 순회하면서 연결된 모든 관리자에게 알림을 전송하는 로직 작성
             */
            activeConnections.entrySet().removeIf(entry -> {
                try {
                    entry.getValue().send(SseEmitter.event()
                            .name("new-order")
                            .data(event));
                    return false; // 전송 성공 - 연결 유지 -> 맵에 계속 보관하자
                } catch (Exception e) {
                    log.debug("알림 전송 실패 (연결 제거): {}", entry.getKey());
                    try {
                        entry.getValue().complete();
                    } catch (Exception ignored) {}
                    return true; // 전송 실패 - 제거
                }
            });

        } catch (Exception e) {
            log.error("주문 알림 처리 실패", e);
        }

        // 전송 후 체크 -> 처음 시작부에서는 문제가 없는데, 전송 과정에서 연결이 실패할 수도 있음.
        // 전송 과정 중 연결이 끊겼다면 알림을 대기 큐로 전송
        if (activeConnections.isEmpty()) {
            rabbitTemplate.convertAndSend("admin.pending.notifications", event);
            log.info("활성 관리자 없음 - 대기 큐로 전송, 주문: {}", event.getOrderId());
        }
    }
}
















