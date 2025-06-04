package com.playdata.orderingservice.client;

import com.playdata.orderingservice.common.dto.CommonResDto;
import com.playdata.orderingservice.ordering.dto.UserResDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "user-service", url = "http://user-service.default.svc.cluster.local:8081") // 호출하고자 하는 서비스 이름 (유레카에 등록된)
public interface UserServiceClient {

    // 요청 방식, 요청 url, 전달하고자 하는 데이터, 응답받고자 하는 데이터의 형태를
    // 추상메서드 형식으로 선언합니다.
    // 그럼 OpenFeign에서 여러분들이 작성한 인터페이스의 구현체를 알아서 만들어 줍니다.

    @GetMapping("/user/findByEmail")
    CommonResDto<UserResDto> findByEmail(@RequestParam String email);

}










