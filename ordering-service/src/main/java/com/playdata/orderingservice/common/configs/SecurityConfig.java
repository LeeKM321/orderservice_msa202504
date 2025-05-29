package com.playdata.orderingservice.common.configs;

import com.playdata.orderingservice.common.auth.JwtAuthFilter;
import com.playdata.orderingservice.common.exception.CustomAuthenticationEntryPoint;
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
        // 스프링 시큐리티에서 기본으로 제공하는 CSRF 토큰 공격을 방지하기 위한 장치 해제.
        // CSRF(Cross Site Request Forgery) 사이트 간 요청 위조
        http.csrf(csrf -> csrf.disable());

//        http.cors(Customizer.withDefaults()); // 직접 커스텀한 CORS 설정을 적용.

        // 세션 관리 상태를 사용하지 않고
        // STATELESS한 토큰을 사용하겠다.
        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // 요청 권한 설정 (어떤 url이냐에 따라 검사를 할 지 말지를 결정)
        http.authorizeHttpRequests(auth -> {
            auth
//                    .requestMatchers("/user/list").hasRole("ROLE_ADMIN")
                    .requestMatchers("/actuator/**", "/demo/**", "/subscribe").permitAll()
                    .anyRequest().authenticated();
        });
        // "/user/create", "/user/doLogin"은 인증 검사가 필요 없다고 설정했고,
        // 나머지 요청들은 권한 검사가 필요하다고 세팅 했습니다.
        // 권한 검사가 필요한 요청들을 어떤 필터로 검사할지를 추가해 주면 됩니다.

        // 스프링 시큐리티가 기본으로 세팅하는 여러가지 필터가 있는데요,
        // 내가 직접 만든 커스텀 필터가 해당 필터를 대체할 것이기 때문에, 그 필터 앞에 세워놓는 겁니다.
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        //  인증 과정에서 예외가 발생할 경우 그 예외를 핸들링 할 객체를 등록
        http.exceptionHandling(exception -> {
            exception.authenticationEntryPoint(customAuthenticationEntryPoint);
        });

        // 설정한 HttpSecurity 객체를 기반으로 시큐리티 설정 구축 및 반환.
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}









