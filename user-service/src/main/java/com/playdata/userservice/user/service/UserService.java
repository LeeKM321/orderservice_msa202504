package com.playdata.userservice.user.service;

import com.playdata.userservice.common.auth.TokenUserInfo;
import com.playdata.userservice.user.dto.UserLoginReqDto;
import com.playdata.userservice.user.dto.UserResDto;
import com.playdata.userservice.user.dto.UserSaveReqDto;
import com.playdata.userservice.user.entity.User;
import com.playdata.userservice.user.repository.UserRepository;
import jakarta.mail.MessagingException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service // @Component 해도 되는데 서비스 계층이니깐...
@RequiredArgsConstructor
@Slf4j
public class UserService {

    // 서비스는 repository에 의존하고 있다. -> repository의 기능을 써야 한다.
    // repository 객체를 자동으로 주입받자. (JPA가 만들어서 컨테이너에 등록해 놓음)
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final MailSenderService mailSenderService;
    private final RedisTemplate<String, Object> redisTemplate;

    // Redis key 상수
    private static final String VERIFICATION_CODE_KEY = "email_verify:code:";
    private static final String VERIFICATION_ATTEMPT_KEY = "email_verify:attempt:";
    private static final String VERIFICATION_BLOCK_KEY = "email_verify:block:";

    // 컨트롤러가 이 메서드를 호출할 것이다.
    // 그리고 지가 전달받은 dto를 그대로 넘길 것이다.
    public User userCreate(UserSaveReqDto dto) {
        Optional<User> foundEmail
                = userRepository.findByEmail(dto.getEmail());

        if (foundEmail.isPresent()) {
            // 이메일 존재? -> 이메일 중복 -> 회원가입 불가!
            // 예외를 일부러 생성시켜서 컨트롤러가 감지하게 할겁니다.
            throw new IllegalArgumentException("이미 존재하는 이메일 입니다!");
        }

        // 이메일 중복 안됨 -> 회원가입 진행하자.
        // dto를 entity 로 변환하는 로직이 필요!
        User user = dto.toEntity(encoder);
        User saved = userRepository.save(user);
        return saved;
    }

    public User login(UserLoginReqDto dto) {
        // 이메일로 user 조회하기
        User user = userRepository.findByEmail(dto.getEmail()).orElseThrow(
                () -> new EntityNotFoundException("User not found!")
        );

        // 비밀번호 확인하기 (암호화 되어있으니 encoder에게 부탁)
        if (!encoder.matches(dto.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        return user;
    }

    public UserResDto myInfo() {
        TokenUserInfo userInfo
                // 필터에서 세팅한 시큐리티 인증 정보를 불러오는 메서드
                = (TokenUserInfo) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();

        User user = userRepository.findByEmail(userInfo.getEmail())
                .orElseThrow(
                        () -> new EntityNotFoundException("User not found!")
                );

        return user.fromEntity();
    }

    public List<UserResDto> userList(Pageable pageable) {
        // Pageable 객체를 직접 생성할 필요 없다. -> 컨트롤러가 보내줌.

        Page<User> users = userRepository.findAll(pageable);

        // 실질적 데이터
        List<User> content = users.getContent();
        List<UserResDto> dtoList = content.stream()
                .map(User::fromEntity)
                .collect(Collectors.toList());

        return dtoList;

    }

    public User findById(String id) {
        return userRepository.findById(Long.parseLong(id)).orElseThrow(
                () -> new EntityNotFoundException("User not found!")
        );
    }

    public UserResDto findByEmail(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new EntityNotFoundException("User not found!")
        );
        return user.fromEntity();
    }

    public String mailCheck(String email) {
        // 차단 상태 확인
        if (isBlocked(email)) {
            throw new IllegalArgumentException("Blocking");
        }

        Optional<User> byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 이메일 입니다!");
        }

        String authNum;
        try {
            // 이메일 전송만을 담당하는 객체를 이용해서 이메일 로직 작성.
            authNum = mailSenderService.joinMail(email);
        } catch (MessagingException e) {
            throw new RuntimeException("이메일 전송 과정 중 문제 발생!");
        }

        // 인증 코드를 Redis 저장
        String key = VERIFICATION_CODE_KEY + email;
        redisTemplate.opsForValue().set(key, authNum, Duration.ofMinutes(1));

        return authNum;
    }

    // 인증 코드 검증 로직
    public Map<String, String> verifyEmail(Map<String, String> map) {
        // 차단 상태 확인
        if (isBlocked(map.get("email"))) {
            throw new IllegalArgumentException("blocking");
        }

        // 레디스에 저장된 인증 코드 조회
        String key = VERIFICATION_CODE_KEY + map.get("email");
        Object foundCode = redisTemplate.opsForValue().get(key);
        if (foundCode == null) { // 조회결과가 null? -> 만료됨!
            throw new IllegalArgumentException("authCode expired!");
        }

        // 인증 시도 횟수 증가
        int attemptCount = incrementAttemptCount(map.get("email"));

        // 조회한 코드와 사용자가 입력한 인증번호 검증
        if (!foundCode.toString().equals(map.get("code"))) {
            // 최대 시도 횟수 초과시 차단
            if (attemptCount >= 3) {
                blockUser(map.get("email"));
                throw new IllegalArgumentException("email blocked!");
            }
            int remainingAttempts = 3 - attemptCount;
            throw new IllegalArgumentException(String.format("authCode wrong!, %d", remainingAttempts));
        }

        log.info("이메일 인증 성공!, email: {}", map.get("email"));
        redisTemplate.delete(key); // 레디스에서 인증번호 삭제
        return map;
    }

    private boolean isBlocked(String email) {
        String key = VERIFICATION_BLOCK_KEY + email;
        return redisTemplate.hasKey(key);
    }

    private void blockUser(String email) {
        String key = VERIFICATION_BLOCK_KEY + email;
        redisTemplate.opsForValue().set(key, "blocked", Duration.ofMinutes(30));
    }

    private int incrementAttemptCount(String email) {
        String key = VERIFICATION_ATTEMPT_KEY + email;
        Object obj = redisTemplate.opsForValue().get(key);

        int count = (obj != null) ? Integer.parseInt(obj.toString()) + 1 : 1;
        redisTemplate.opsForValue().set(key, count, Duration.ofMinutes(1));

        return count;
    }
}









