package com.playdata.orderingservice.ordering.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playdata.orderingservice.client.ProductServiceClient;
import com.playdata.orderingservice.client.UserServiceClient;
import com.playdata.orderingservice.common.auth.TokenUserInfo;
import com.playdata.orderingservice.common.dto.CommonResDto;
import com.playdata.orderingservice.ordering.controller.SseController;
import com.playdata.orderingservice.ordering.dto.OrderingListResDto;
import com.playdata.orderingservice.ordering.dto.OrderingSaveReqDto;
import com.playdata.orderingservice.ordering.dto.ProductResDto;
import com.playdata.orderingservice.ordering.dto.UserResDto;
import com.playdata.orderingservice.ordering.entity.OrderDetail;
import com.playdata.orderingservice.ordering.entity.OrderStatus;
import com.playdata.orderingservice.ordering.entity.Ordering;
import com.playdata.orderingservice.ordering.repository.OrderingRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.ws.rs.ServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class OrderingService {

    private final OrderingRepository orderingRepository;
    private final RestTemplate restTemplate;
    private final SseController sseController;

    // feign client 구현체 주입 받기
    private final UserServiceClient userServiceClient;
    private final ProductServiceClient productServiceClient;

    // CircuitBreaker 동작 객체 주입
    private final CircuitBreakerFactory circuitBreakerFactory;

    public Ordering createOrder(List<OrderingSaveReqDto> dtoList,
                                TokenUserInfo userInfo) {
        UserResDto userDto;
        Ordering ordering;

        try {
            userDto = getUserResDto(userInfo.getEmail());
            log.info("user-service로부터 전달받은 결과: {}", userDto);

            // Ordering(주문) 객체 생성
            ordering = Ordering.builder()
                    .userId(userDto.getId())
                    .userEmail(userDto.getEmail())
                    .orderDetails(new ArrayList<>()) // 아직 주문 상세 들어가기 전.
                    .build();

            // 여기서 dtoList를 JSON으로 직렬화해서 ordering에 저장
            // 사용자의 주문 상세 내역을 DB에 꽂아버리기 위해서 필드를 하나 추가해 놓음. -> JSON 문자열 필드
            // 지금까지는 문제가 없었지만, 이후에 product쪽에 장애 발생하면 결국 재처리 들어가야 되거든요.
            // 미리 주문 상세 내역을 JSON으로 바꿔서 Ordering쪽에 때려 놓자.
            ObjectMapper mapper = new ObjectMapper();
            String dtoJson = mapper.writeValueAsString(dtoList);
            ordering.setOriginalRequestJson(dtoJson); // ordering 엔티티에 필드 미리 추가해둬야 함


        } catch (Exception e) {
            log.error("user-service 장애. 주문 보류로 처리합니다. {}", e.getMessage());

            // 일단 Ordering을 생성하긴 하는데, 정상처리는 아니고 보류(PENDING) 처리만 진행.
            // 가지고 있는 정보는 전부 다 채워 넣자 -> 나중에 재처리 할거니깐.
            Ordering pendingUserFail = Ordering.builder()
                    .userEmail(userInfo.getEmail())
                    .orderStatus(OrderStatus.PENDING_USER_FAILURE)
                    .orderDetails(new ArrayList<>())
                    .build();

            try {
                ObjectMapper mapper = new ObjectMapper();
                String dtoJson = mapper.writeValueAsString(dtoList);
                pendingUserFail.setOriginalRequestJson(dtoJson);
            } catch (JsonProcessingException ex) {
                e.printStackTrace();
            }

//            // 주문 내역을 List<OrderDetail>로 바꾸는 로직
//            // 재처리 시에 이사람이 어떤 상품을 몇 개 주문했는지에 대한 정보는 있어야 하니깐.
//            List<OrderDetail> detailList = dtoList.stream()
//                    .map(dto -> toOrderDetail(dto, pendingUserFail))
//                    .collect(Collectors.toList());
//
//            pendingUserFail.getOrderDetails().addAll(detailList);

            return orderingRepository.save(pendingUserFail); // 여기서 바로 리턴 끝

        }

        // product쪽에 요청 보내는 로직은 따로 메서드로 나누었습니다. (너무 길어서...)
        processOrderToProductService(dtoList, userDto.getId(), ordering);

        // 모든 로직에 장애가 없었다면 주문 확정(status가 ORDERED로 처리)
        Ordering savedOrdering = orderingRepository.save(ordering);

        // 관리자에게 주문이 생성되었다는 알림을 전송
        sseController.sendOrderMessage(savedOrdering);

        return savedOrdering;
    }

    public UserResDto getUserResDto(String email) {
        // 서킷 브레이커 적용하기
        CircuitBreaker userCircuit = circuitBreakerFactory.create("userService");

        // Ordering 객체를 생성하기 위해 회원 정보를 얻어오자.
        // 우리가 가진 유일한 정보는 토큰 안에 들어있던 이메일 뿐입니다...
        // 이메일을 가지고 요청을 보내자 -> user-service
        CommonResDto<UserResDto> byEmail = userCircuit.run(
                // 정상 호출
                () -> userServiceClient.findByEmail(email)
//                    throwable -> {
//                        // 장애가 발생한 상황에 실행할 객체 선언 (fallback)
                      // 이후의 로직을 계속 실행하고 싶을 때 사용.
                      // 여기에 작성하는 로직으로 정상 호출을 완벽하게 대체할 수 있을 때 사용.
                      // 우리의 상황 -> user-service에서 장애 발생하면 id를 받아올 방법이 아예 없음;;
                      // 정상 호출을 완벽하게 대체할 수 없음. try-catch로 주문 흐름을 보류쪽으로 빼서 작업.
//                    }
        );
        return byEmail.getResult();
    }

    public void processOrderToProductService(List<OrderingSaveReqDto> dtoList,
                                             Long userId,
                                             Ordering ordering
    ) {
        log.info("주문 객체 ID: {}", ordering.getId());
        log.info("주문 상태: {}", ordering.getOrderStatus());


        // 주문 상세 내역에 대한 처리를 반복해서 지정.
        for (OrderingSaveReqDto dto : dtoList) {

            try {
                // dto 안에 있는 상품 id를 이용해서 상품 정보 얻어오자.
                // product 객체를 조회하자 -> product-service에게 요청해야 함!
                ProductResDto prodResDto = getProductInfo(dto.getProductId());

                log.info("product-service로부터 받아온 결과: {}", prodResDto);
                int stockQuantity = prodResDto.getStockQuantity();

                // 재고 넉넉하게 있는지 확인
                int quantity = dto.getProductQuantity();
                if (stockQuantity < quantity) {
                    throw new IllegalArgumentException("재고 부족!");
                }

                deductStock(prodResDto, quantity);

                // 주문 상세 내역 엔터티 생성
                OrderDetail orderDetail = OrderDetail.builder()
                        .productId(dto.getProductId())
                        .ordering(ordering)
                        .quantity(quantity)
                        .build();

                log.info("orderDetail: {}", orderDetail);

                // 주문 내역 리스트에 상세 내역을 add하기.
                // (cascadeType.PERSIST로 세팅했기 때문에 함께 INSERT가 진행될 것!)
                ordering.getOrderDetails().add(orderDetail);
            } catch (ServiceUnavailableException e) {
                // product와의 통신에서는 경우의 수가 2가지 이기 때문에
                // 에러 메세지에 포함 되어있는 단어의 유무에 따라 status를 다르게 세팅.
                ordering.updateStatus(
                        e.getMessage().contains("차감") ?
                                OrderStatus.PENDING_PROD_STOCK_UPDATE :
                                OrderStatus.PENDING_PROD_NOT_FOUND
                );
                // catch 블록에서는 이 상품만 다시 add
                ordering.getOrderDetails().add(toOrderDetail(dto, ordering));
                orderingRepository.save(ordering);

            } catch (IllegalArgumentException e) {
                // 이 상황은 주문 보류가 아니에요! 서비스 장애 때문에 벌어진 상황이 아님!
                log.warn("재고 부족으로 주문 불가! 상품 ID: {}, 오류: {}", dto.getProductId(), e.getMessage());
                throw e; // 클라이언트 예외니까 그대로 던짐 -> 컨트롤러 -> 전역 예외 핸들러가 클라이언트로 상태를 전달.
            }
        }
        log.info("마지막 ordering: {}", ordering.getId());
        orderingRepository.save(ordering);
    }

    // 재고를 주문수량만큼 감소시켜달라는 요청을 전담하는 메서드
    private void deductStock(ProductResDto prodResDto, int quantity) {
        try {
            CircuitBreaker updateCircuit = circuitBreakerFactory.create("productServiceUpdate");

            updateCircuit.run(() -> {
                prodResDto.setStockQuantity(prodResDto.getStockQuantity() - quantity);
                productServiceClient.updateQuantity(prodResDto);
                return null;
            });

        } catch (Exception e) {
            log.error("재고 차감 실패! 상품 ID: {}, 오류: {}", prodResDto.getId(), e.getMessage());
            throw new ServiceUnavailableException("재고 차감 실패");
        }
    }

    // 번호를 전달받아서 product-service로부터 상품 정보 조회를 전담하는 메서드
    private ProductResDto getProductInfo(Long productId) {
        try {
            CircuitBreaker productCircuit = circuitBreakerFactory.create("productService");

            // 여기도 장애 발생 시 fallback 로직을 작성하지 않았습니다. 왜냐?
            // 대체 로직이 정상 상황을 완벽하게 대체할 수가 없으니깐... -> 주문 보류!
            // user-service쪽과 마찬가지로 아예 예외로 빼서 나중에 재처리 해 줄겁니다.
            CommonResDto<ProductResDto> byId = productCircuit.run(
                    () -> productServiceClient.findById(productId)
            );
            return byId.getResult();
        } catch (Exception e) {
            log.error("상품 정보 조회 실패! 상품 ID: {}, 오류: {}", productId, e.getMessage());
            throw new ServiceUnavailableException("상품 정보 조회 실패");
        }
    }

    private OrderDetail toOrderDetail(OrderingSaveReqDto dto, Ordering ordering) {
        return OrderDetail.builder()
                .productId(dto.getProductId())
                .quantity(dto.getProductQuantity())
                .ordering(ordering)
                .build();
    }

    public List<OrderingListResDto> myOrder(final TokenUserInfo userInfo) {
        String email = userInfo.getEmail();

        // 이메일로는 주문 회원 정보를 알 수가 없음. (id로 되어 있으니까)
        CommonResDto<UserResDto> byEmail
                = userServiceClient.findByEmail(email);
        UserResDto userDto = byEmail.getResult();

        // 해당 사용자의 주문 내역 전부 가져오기.
        List<Ordering> orderingList
                = orderingRepository.findByUserId(userDto.getId());

        List<Long> productIdList = new ArrayList<>();
        // 주문 내역에서 모든 상품 ID를 추출한 후
        // product-service에게 상품 정보를 요청.

        /*
         OrderingListResDto -> OrderDetailDto(static 내부 클래스)
         {
            id: 주문번호,
            userEmail: 주문한 사람 이메일,
            orderStatus: 주문 상태
            orderDetails: [
                {
                    id: 주문상세번호,
                    productName: 상품명,
                    count: 수량
                },
                {
                    id: 주문상세번호,
                    productName: 상품명,
                    count: 수량
                },
                {
                    id: 주문상세번호,
                    productName: 상품명,
                    count: 수량
                }
                ...
            ]
         }
         */

//        for (Ordering ordering : orderingList) {
//            List<OrderDetail> orderDetailList = ordering.getOrderDetails();
//            for (OrderDetail orderDetail : orderDetailList) {
//                Long productId = orderDetail.getProductId();
//                if (!productIdList.contains(productId)) {
//                    productIdList.add(productId);
//                }
//            }
//        }

        List<Long> productIds = orderingList.stream() // 스트림 준비
                // flatMap: 하나의 주문 내역에서 상세 주문 내역 리스트를 꺼낸 후 하나의 스트림으로 평탄화
                /* flatMap의 동작 원리
                [
                    Ordering 1 -> [OrderDetail1, OrderDetail2]
                    Ordering 2 -> [OrderDetail3]
                    Ordering 3 -> [OrderDetai4, OrderDetail5, OrderDetail6]
                ]

                [OrderDetail1, OrderDetail2, OrderDetail3, OrderDetail4, OrderDetail5, OrderDetail6]
                 */

                .flatMap(ordering -> ordering.getOrderDetails().stream())
                .map(orderDetail -> orderDetail.getProductId())
                .distinct()
                .collect(Collectors.toList());

        // product-service에게 상품 정보를 달라고 요청해야 함.
        CommonResDto<List<ProductResDto>> products
                = productServiceClient.getProducts(productIds);
        List<ProductResDto> dtoList = products.getResult();

        // product-service에게 받아온 리스트를 필요로 하는 정보로만 맵으로 맵핑.
        Map<Long, String> productIdToNameMap = dtoList.stream()
                .collect(Collectors.toMap(
                        dto -> dto.getId(), // key
                        dto -> dto.getName() // value
                ));

        // Ordering 객체를 DTO로 변환하자. 주문 상세에 대한 변환도 따로 처리.
        return orderingList.stream()
                .map(ordering -> ordering.fromEntity(email, productIdToNameMap))
                .collect(Collectors.toList());
    }

    public Ordering cancelOrder(long id) {
        Ordering ordering = orderingRepository.findById(id).orElseThrow(
                () -> new EntityNotFoundException("주문 없음!")
        );
        // 주문 취소 -> 주문 entity의 status를 CANCELED로 변경
        // 당시 주문했던 상품들의 수량을 원상복구 해 놓아야 한다.
        // 주문1 -> 상품34: 3개, 상품17: 5개 -> 주문 들어갔을 때 감소했으니까, 주문 취소때는 증가 시켜야 한다.
        List<OrderDetail> orderDetailList = ordering.getOrderDetails();
        Map<Long, Integer> map = orderDetailList.stream()
                .collect(Collectors.toMap(
                        detail -> detail.getProductId(),
                        detail -> detail.getQuantity()
                ));
        log.info("toMap의 결과: {}", map);
        productServiceClient.cancelProduct(map);


        // 더티 체킹 (save를 하지 않아도 변경을 감지해서 update를 날려 준다.)
        ordering.updateStatus(OrderStatus.CANCELED);
//        orderingRepository.save(ordering);
        return ordering;
    }


}







