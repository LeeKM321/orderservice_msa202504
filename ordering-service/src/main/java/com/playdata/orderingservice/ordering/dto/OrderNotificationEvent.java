package com.playdata.orderingservice.ordering.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.playdata.orderingservice.ordering.entity.Ordering;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderNotificationEvent {

    private Long orderId; // 주문 ID (관리자가 주문을 찾기 위해)
    private String customerEmail;  // 고객 이메일 (누가 주문했는지)
    private Long customerId;  // 고객 ID (추가 조회용)
    private String orderStatus;  // 주문 상태 (ORDERED, CANCELED 등)
    private int totalItems;  // 총 상품 개수 (간단한 요약 정보)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime orderTime; // 주문 시간 (언제 주문했는지)
    private List<OrderItemInfo> orderItems; // 주문 상품 목록 (상세 정보)

    @Getter @Setter @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemInfo {
        private Long productId; // 어떤 상품인지
        private int quantity; // 몇 개 주문했는지
    }

    // Ordering 엔티티에서 이벤트 DTO 생성
    // 주문이 완료되면, 완료된 주문 정보 객체(Ordering)을 기반으로
    // 알림 메세지에 세팅하고 싶은 값들만 골라서 DTO 생성 후 RabbitMQ에게 전달.
    public static OrderNotificationEvent fromOrdering(Ordering ordering) {
        // OrderDetail 리스트를 OrderItemInfo 리스트로 변환
        List<OrderItemInfo> items = ordering.getOrderDetails().stream()
                .map(detail -> OrderItemInfo.builder()
                        .productId(detail.getProductId())
                        .quantity(detail.getQuantity())
                        .build())
                .toList();

        return OrderNotificationEvent.builder()
                .orderId(ordering.getId())
                .customerEmail(ordering.getUserEmail())
                .customerId(ordering.getUserId())
                .orderStatus(ordering.getOrderStatus().name())
                .totalItems(items.stream().mapToInt(OrderItemInfo::getQuantity).sum())
                .orderTime(LocalDateTime.now())
                .orderItems(items)
                .build();
    }


}










