package com.playdata.userservice.user.entity;

import com.playdata.userservice.common.entity.Address;
import com.playdata.userservice.user.dto.UserResDto;
import jakarta.persistence.*;
import lombok.*;

// Entity에 Setter를 구현하지 않은 이유는 Entity 자체가 DB와 연동하기 위한 객체.
// DB에 삽입되는 데이터, DB에서 조회된 데이터는 그 자체로 사용하고 수정되지 않게끔
// setter를 사용하지 않는 것을 권장.
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tbl_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20, nullable = false)
    private String name;

    @Column(nullable = true)
    private String password;

    @Column(unique = true, nullable = false)
    private String email;

    @Embedded  // @Embeddable로 선언된 값 대입 (기본 생성자 필수)
    private Address address;

    @Enumerated(EnumType.STRING)
    @Builder.Default // builder 패턴 사용해서 객체 초기화 시 초기값으로 세팅
    private Role role =  Role.USER;

    @Column
    private String socialId; // 소셜 로그인 고유 ID

    @Column
    private String profileImage; // 프로필 이미지 url

    @Column
    private String socialProvider; // GOOGLE, KAKAO, NAVER, null(일반 로그인)

    // DTO에 Entity 변환 메서드가 있는 거처럼
    // Entity에도 응답용 DTO 변환 메서드를 세팅해서 언제든 변환이 자유롭도록 작성.
    public UserResDto fromEntity() {
        return UserResDto.builder()
                .id(id)
                .name(name)
                .email(email)
                .role(role)
                .address(address)
                .profileImage(profileImage)
                .socialProvider(socialProvider)
                .build();
    }

}






