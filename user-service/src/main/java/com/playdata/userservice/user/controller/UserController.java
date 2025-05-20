package com.playdata.userservice.user.controller;

import com.playdata.userservice.common.auth.JwtTokenProvider;
import com.playdata.userservice.common.dto.CommonErrorDto;
import com.playdata.userservice.common.dto.CommonResDto;
import com.playdata.userservice.user.dto.UserLoginReqDto;
import com.playdata.userservice.user.dto.UserResDto;
import com.playdata.userservice.user.dto.UserSaveReqDto;
import com.playdata.userservice.user.entity.User;
import com.playdata.userservice.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/user") // user 관련 요청은 /user로 시작한다고 가정.
@RequiredArgsConstructor
@Slf4j
public class UserController {

    // 컨트롤러는 서비스에 의존하고 있다. (요청과 함께 전달받은 데이터를 서비스에게 넘겨야 함!)
    // 빈 등록된 서비스 객체를 자동으로 주입 받자!
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;

    // 야호
    // 기존에는 yml 값 가지고 올때 @Value를 사용해서 끌고 옴
    // Environment 객체를 통해 yml에 있는 프로퍼티에 직접 접근이 가능합니다.
    private final Environment env;

    /*
     프론트 단에서 회원 가입 요청 보낼때 함께 보내는 데이터 (JSON) -> dto로 받자.
     {
        name: String,
        email: String,
        password: String,
        address: {
            city: String,
            street: String,
            zipCode: String
        }
     }
     */
    @PostMapping("/create")
    public ResponseEntity<?> userCreate(@Valid @RequestBody UserSaveReqDto dto) {
        // 화면단에서 전달된 데이터를 DB에 넣자.
        // 혹시 이메일이 중복되었는가? -> 이미 이전에 회원가입을 한 회원이라면 거절.
        // dto를 DB에 바로 때려? -> dto를 entity로 바꾸는 로직 추가.


        User saved = userService.userCreate(dto);
        // ResponseEntity는 응답을 줄 때 다양한 정보를 한번에 포장해서 넘길 수 있습니다.
        // 요청에 따른 응답 상태 코드, 응답 헤더에 정보를 추가, 일관된 응답 처리를 제공합니다.

        CommonResDto resDto
                = new CommonResDto(HttpStatus.CREATED,
                "User Created", saved.getName());

        return new ResponseEntity<>(resDto, HttpStatus.CREATED);
    }

    @PostMapping("/doLogin")
    public ResponseEntity<?> doLogin(@RequestBody UserLoginReqDto dto) {
        User user = userService.login(dto);

        // 회원 정보가 일치한다면 -> 로그인 성공.
        // 로그인 유지를 해 주고 싶다.
        // 백엔드는 요청이 들어왔을 때 이 사람이 이전에 로그인 성공 한 사람인지 알 수가 없다.
        // 징표를 하나 만들어 주겠다. -> JWT를 발급해서 클라이언트에게 전달해 주겠다!
        // Access Token 발급 -> 수명이 짧습니다. (토큰 탈취 방지)
        String token
                = jwtTokenProvider.createToken(user.getEmail(), user.getRole().toString());

        // Refresh Token을 생성해 주겠다.
        // Access Token 수명이 만료되었을 경우 Refresh Token을 확인해서 리프레시가 유효한 경우
        // 로그인 없이 Access Token을 재발급 해주는 용도로 사용.
        String refreshToken
                = jwtTokenProvider.createRefreshToken(user.getEmail(), user.getRole().toString());

        // refreshToken을 DB에 저장하자. (redis)
//        userService.saveRefreshToken(user.getEmail(), refreshToken);
        redisTemplate.opsForValue().set("user:refresh:" + user.getId(), refreshToken, 2, TimeUnit.MINUTES);

        // Map을 이용해서 사용자의 id와 token을 포장하자.
        Map<String, Object> loginInfo = new HashMap<>();
        loginInfo.put("token", token);
        loginInfo.put("id", user.getId());
        loginInfo.put("role", user.getRole().toString());

        CommonResDto resDto
                = new CommonResDto(HttpStatus.OK,
                "Login Success", loginInfo);
        return new ResponseEntity<>(resDto, HttpStatus.OK);
    }

    // 회원 정보 조회 (관리자 전용) -> ADMIN만 회원 전체 목록을 조회할 수 있다.
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/list")
    // 컨트롤러 파라미터 Pageable 선언하면 페이징 파라미터 처리를 쉽게 할 수 있음.
    // /list?number=1&size=10&sort=name,desc 요런 식으로.
    // 요청 시 쿼리스트링이 전달되지 않으면 기본값 0, 20, unsorted
    public ResponseEntity<?> getUserList(Pageable pageable) {
        List<UserResDto> dtoList = userService.userList(pageable);
        CommonResDto resDto
                = new CommonResDto(HttpStatus.OK, "userList 조회 성공", dtoList);

        return ResponseEntity.ok().body(resDto);
    }

    // 회원 정보 조회 (마이페이지) -> 로그인 한 회원만이 요청할 수 있습니다.
    // 일반 회원용 정보 조회
    @GetMapping("/myInfo")
    public ResponseEntity<?> getMyInfo() {
        UserResDto dto = userService.myInfo();
        CommonResDto resDto
                = new CommonResDto(HttpStatus.OK, "myInfo 조회 성공", dto);

        return new ResponseEntity<>(resDto, HttpStatus.OK);
    }

    // access token이 만료되어 새 토큰을 요청
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> map) {
        String id = map.get("id");
        log.info("/user/refresh: POST, id: {}", id);
        // redis에 해당 id로 조회되는 내용이 있는지 확인
        Object obj = redisTemplate.opsForValue().get("user:refresh:" + id);
        log.info("obj: {}", obj);
        if (obj == null) { // refresh token이 수명이 다됨.
            return new ResponseEntity<>(new CommonErrorDto(
                    HttpStatus.UNAUTHORIZED, "EXPIRED_RT"),
                    HttpStatus.UNAUTHORIZED);
        }
        // 새로운 access token을 발급
        User user = userService.findById(id);
        String newAccessToken
                = jwtTokenProvider.createToken(user.getEmail(), user.getRole().toString());

        Map<String, Object> info = new HashMap<>();
        info.put("token", newAccessToken);
        CommonResDto resDto
                = new CommonResDto(HttpStatus.OK, "새 토큰 발급됨", info);
        return ResponseEntity.ok().body(resDto);
    }

    // ordering-service가 회원 정보를 원할 때 이메일을 보냅니다.
    // 그 이메일을 가지고 ordering-service가 원하는 회원 정보를 리턴하는 메서드.
    @GetMapping("/findByEmail")
    public ResponseEntity<?> getUserByEmail(@RequestParam String email) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        log.info("getUserByEmail: email: {}", email);
        UserResDto dto = userService.findByEmail(email);
        CommonResDto resDto
                = new CommonResDto(HttpStatus.OK, "이메일로 회원 조회 완료", dto);
        return ResponseEntity.ok().body(resDto);
    }

    @GetMapping("/health-check")
    public String healthCheck() {
        String msg = "It's Working in User-service!\n";
        msg += "token.expiration_time: " + env.getProperty("token.expiration_time");
        msg += "token.secret: " + env.getProperty("token.secret");
        msg += "aws.accessKey: " + env.getProperty("aws.accessKey");
        msg += "aws.secretKey: " + env.getProperty("aws.secretKey");

        return msg;
    }


}








