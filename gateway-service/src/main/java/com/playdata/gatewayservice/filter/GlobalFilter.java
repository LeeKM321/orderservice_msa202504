package com.playdata.gatewayservice.filter;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@RefreshScope
public class GlobalFilter
        extends AbstractGatewayFilterFactory<GlobalFilter.Config> {

    // Filter가 빈으로 등록될 때 부모 클래스의 생성자에게
    // 이미 정적으로 세팅된 특정 설정값을 전달합니다.
    public GlobalFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        // 부모는 전달받은 설정값을 필터가 동작할 때 (apply가 호출될 때) 사용할 수 있도록
        // 매개값으로 전달해 줍니다. -> 필터의 동작 등을 제어할 수 있습니다.
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            log.info("Global Filter active! base Message = {}", config.getBaseMessage());
            if (config.isPreLogger()) {
                log.info("Request URI: {}",  request.getURI());
            }

            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                if (config.isPostLogger()) {
                    log.info("Global Post Filter active! response code = {}", response.getStatusCode());
                }
            }));
        };
    }

    @Getter @Setter @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Config {
        // 필터 동작을 동적으로 변경하거나 설정하기 위해 사용합니다. (선택 사항)
        // 필터 동작을 YAML 설정 파일을 통해 외부에서 동적으로 설정할 수 있습니다.
        private String baseMessage;
        private boolean preLogger;
        private boolean postLogger;
    }


}





