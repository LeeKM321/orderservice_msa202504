package com.playdata.userservice.user.controller;

import com.playdata.userservice.common.dto.CommonErrorDto;
import com.playdata.userservice.common.dto.CommonResDto;
import com.playdata.userservice.common.dto.KakaoUserDto;
import com.playdata.userservice.user.dto.UserLoginReqDto;
import com.playdata.userservice.user.dto.UserResDto;
import com.playdata.userservice.user.dto.UserSaveReqDto;
import com.playdata.userservice.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// Swagger 전용 인터페이스를 하나 선언해서 비즈니스 로직 vs 문서화 로직 완전히 분리
// 컨트롤러는 본연의 역할에만 집중
@Tag(name = "사용자 관리(UserController)", description = "사용자 회원가입, 로그인, 정보 조회 등을 관리하는 API 입니다.")
public interface UserControllerDocs {

    @Operation(
            summary = "회원가입",
            description = """
                새로운 사용자를 등록합니다.
                
                ## 요청 데이터
                - 이름, 이메일, 비밀번호, 주소 정보가 필요합니다.
                - 이메일은 중복될 수 없습니다.
                
                ## 응답
                - 성공시 생성된 사용자 이름을 반환합니다.        
            """,
            tags = {"인증"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "회원가입 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CommonResDto.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "status": "201",
                                                "message": "User Created",
                                                "data": "홍길동"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (이메일 중복, 유효성 검사 실패 등)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CommonErrorDto.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "status": "400",
                                                "message": "이미 존재하는 이메일입니다!"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<?> userCreate(
            @Parameter(description = "회원가입 정보", required = true)
            @Valid @RequestBody UserSaveReqDto dto);

    public ResponseEntity<?> doLogin(@RequestBody UserLoginReqDto dto);

    public ResponseEntity<?> getUserList(Pageable pageable);

    @Operation(
            summary = "내 정보 조회",
            description = """
                로그인 한 사용자의 개인 정보를 조회합니다.
                
                ## 인증
                - JWT 토큰이 필요합니다.
                - 본인의 정보만 조회가 가능합니다.     
            """,
            tags = {"인증", "사용자 정보"},
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CommonResDto.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "status": "OK",
                                                "message": "myInfo 조회 성공",
                                                "data": {
                                                    "id": 1,
                                                    "name": "홍길동",
                                                    "email": "hong@example.com",
                                                    "role": "USER",
                                                    "address": {
                                                        "city": "서울",
                                                        "street": "강남대로 123",
                                                        "zipCode": "06234"
                                                    }
                                                }
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증 실패 (유효하지 않은 토큰, 토큰의 부재 등)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CommonErrorDto.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                                "status": "401",
                                                "message": "유효하지 않은 토큰입니다!"
                                            }
                                            """
                            )
                    )
            )
    })
    public ResponseEntity<?> getMyInfo();

    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> map);

    public ResponseEntity<?> getUserByEmail(@RequestParam String email);

    public ResponseEntity<?> emailValid(@RequestBody Map<String, String> map);

    public ResponseEntity<?> verifyEmail(@RequestBody Map<String, String> map);



}
