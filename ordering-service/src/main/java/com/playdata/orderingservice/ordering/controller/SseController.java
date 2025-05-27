package com.playdata.orderingservice.ordering.controller;

import com.playdata.orderingservice.common.auth.TokenUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RestController
@Slf4j
public class SseController {

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
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

            // 30초마다 heartbeat 메시지를 전송해서 연결 유지
            // 클라이언트에서 사용하는 EventSourcePolyfill이 45초동안 활동이 없으면 지맘대로 연결 종료.
            Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
                // 일정하게 동작시킬 로직을 작성
                try {
                    emitter.send(
                            SseEmitter.event()
                                    .name("heartbeat")
                                    .data("keep-alive") // 클라이언트 단이 살아있는지 확인
                    );
                } catch (IOException e) {
                    e.printStackTrace();
                    log.info("Failed to send heartbeat");
                }
            }, 30, 30, TimeUnit.SECONDS);


        } catch (IOException e) {
            e.printStackTrace();
        }

        return emitter;

    }

}









