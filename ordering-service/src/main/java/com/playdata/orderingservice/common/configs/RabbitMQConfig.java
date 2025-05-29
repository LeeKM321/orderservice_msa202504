package com.playdata.orderingservice.common.configs;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    /*
    목표: 주문이 생성되면 → 관리자에게 알림이 가도록 "메시지 전달 경로" 만들기

    [주문 생성] → [Exchange] → [Queue] → [관리자 알림]
     */

    /**
     * Exchange = 우체국 역할
     * 메시지가 들어오면 "어느 큐로 보낼지" 결정하는 곳
     * TopicExchange = 패턴 매칭으로 라우팅 (예: order.*, user.*)

     * 주문 관련 메시지들을 처리하는 교환소라는 의미
     * 이름은 마음대로 정할 수 있어요 ("my-exchange", "쇼핑몰교환소" 등)
     */
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange("order.exchange");
    }

    /**
     * Queue = 사서함 역할
     * 관리자에게 보낼 알림 메시지들이 쌓이는 곳
     * durable = 서버 재시작해도 큐가 사라지지 않음
     *
     * admin = 관리자용
     * order = 주문 관련
     * notifications = 알림용
     * → "관리자 주문 알림 큐"라는 의미
     *
     * Time To Live = 메시지 유효기간
     * 1시간(3600초) 후에도 처리 안 되면 메시지 삭제
     * 관리자가 1시간 동안 접속 안 하면 오래된 알림은 의미 없으니까!
     */
    @Bean
    public Queue orderQueue() {
        return QueueBuilder.durable("admin.order.notifications")
                .withArgument("x-message-ttl", 3600000)
                .build();
    }

    // 대기 알림용 큐 (알림 전송 대상 관리자가 없을 시 메시지 누적)
    @Bean
    public Queue pendingNotificationQueue() {
        return QueueBuilder.durable("admin.pending.notifications")
                .withArgument("x-message-ttl", 86400000) // 24시간
                .build();
    }

    /**
     * Exchange와 Queue를 연결하는 규칙
     * "order.created" 패턴의 메시지가 오면 → admin.order.notifications 큐로 보내라!
     */
    @Bean
    public Binding adminNotificationBinding() {
        return BindingBuilder
                .bind(orderQueue())
                .to(orderExchange())
                .with("order.created");
    }

    /**
     * 우리가 보낼 객체 (OrderNotificationEvent -> DTO)를 JSON으로 변환
     * 받을 때도 JSON을 다시 객체로 변환
     */
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // RabbitTemplate 설정
    // 역할: 메시지 발송자 (Producer) - "편지를 우체통에 넣는 도구"
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }

    // Listener 설정
    // 역할: 메시지 수신자 (Consumer) - "사서함에서 편지를 자동으로 꺼내주는 도구"
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory
                = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        return factory;
    }


}








