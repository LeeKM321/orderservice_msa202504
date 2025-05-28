package com.playdata.orderingservice.ordering.service;

import com.playdata.orderingservice.ordering.dto.OrderNotificationEvent;
import com.playdata.orderingservice.ordering.entity.Ordering;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderNotificationService {
    /*
    역할: "주문 정보를 받아서 RabbitMQ로 알림 메시지를 보내는 택배 기사"

    [OrderingService] → [OrderNotificationService] → [RabbitMQ] → [관리자]
       (주문 완료!)           ("알림 보내드릴게요!")        (메시지 전달)   (알림 받음!)
     */

    // 우리가 만든 exchange, queue로 메시지를 보낼 수 있게 해줌.
    private final RabbitTemplate rabbitTemplate;

    public void sendNewOrderNotification(Ordering ordering) {
        try {
            // 주문 완료된 것만 알림 발송
            if (ordering.getOrderStatus().name().equals("ORDERED")) {
                // 알림 전용 DTO 생성
                OrderNotificationEvent event = OrderNotificationEvent.fromOrdering(ordering);

                // RabbitMQ로 메시지 발송
                rabbitTemplate.convertAndSend(
                        "order.exchange", // RabbitMQConfig에서 만든 Exchange
                        "order.created", // Routing Key (어느 큐로 보낼지 결정)
                        event // 보낼 데이터 (JSON으로 자동 변환됨)
                );

                log.info("Order notification sent to admin: orderId={}, customer={}"
                        , ordering.getId(), ordering.getUserEmail());
            }
        } catch (Exception e) {
            log.error("Failed to send order notification to admin: orderId={}"
                    , ordering.getId(), e);
            // 알림 실패해도 주문 처리는 계속 진행
            // 알림은 부가 기능이니까 실패해도 주문은 성공해야 함.
        }
    }


}



