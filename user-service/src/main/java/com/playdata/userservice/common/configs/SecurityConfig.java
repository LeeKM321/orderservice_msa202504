package com.playdata.userservice.common.configs;

import com.playdata.userservice.common.auth.JwtAuthFilter;
import com.playdata.userservice.common.exception.CustomAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // 권한 검사를 컨트롤러의 메서드에서 전역적으로 수행하기 위한 설정.
@RequiredArgsConstructor
public class SecurityConfig {

    // 필터 등록을 위해서 객체가 필요 -> 빈 등록된 객체를 자동 주입.
    private final JwtAuthFilter jwtAuthFilter;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    // 시큐리티 기본 설정 (권한 처리, 초기 로그인 화면 없애기 등등...)
    @Bean // 이 메서드가 리턴하는 시큐리티 설정을 빈으로 등록하겠다.
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());

//        http.cors(Customizer.withDefaults()); // 직접 커스텀한 CORS 설정을 적용.

        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // 요청 권한 설정 (어떤 url이냐에 따라 검사를 할 지 말지를 결정)
        http.authorizeHttpRequests(auth -> {
            auth
//                    .requestMatchers("/user/list").hasRole("ROLE_ADMIN")
                    .requestMatchers("/user/create",
                                    "/user/doLogin",
                                    "/user/refresh",
                                    "/user/findByEmail",
                            "/user/health-check",
                            "/user/email-valid",
                            "/user/verify",
                            "/actuator/**").permitAll()
                    .anyRequest().authenticated();
        });

        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // 설정한 HttpSecurity 객체를 기반으로 시큐리티 설정 구축 및 반환.
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}









