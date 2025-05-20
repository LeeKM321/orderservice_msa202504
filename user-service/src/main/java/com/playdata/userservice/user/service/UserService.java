package com.playdata.userservice.user.service;

import com.playdata.userservice.common.auth.TokenUserInfo;
import com.playdata.userservice.user.dto.UserLoginReqDto;
import com.playdata.userservice.user.dto.UserResDto;
import com.playdata.userservice.user.dto.UserSaveReqDto;
import com.playdata.userservice.user.entity.User;
import com.playdata.userservice.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service // @Component 해도 되는데 서비스 계층이니깐...
@RequiredArgsConstructor
public class UserService {

    // 메롱
    // 서비스는 repository에 의존하고 있다. -> repository의 기능을 써야 한다.
    // repository 객체를 자동으로 주입받자. (JPA가 만들어서 컨테이너에 등록해 놓음)
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;

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
}









