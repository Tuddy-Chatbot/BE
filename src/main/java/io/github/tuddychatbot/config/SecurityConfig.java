package io.github.tuddychatbot.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;

import io.github.tuddychatbot.security.CsrfCookieFilter;
import io.github.tuddychatbot.security.JpaUserDetailsService;
import io.github.tuddychatbot.security.oauth.CustomOAuth2UserService;
import io.github.tuddychatbot.security.oauth.OAuth2LoginSuccessHandler;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JpaUserDetailsService jpaUserDetailsService;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider p = new DaoAuthenticationProvider();
        p.setUserDetailsService(jpaUserDetailsService);
        p.setPasswordEncoder(passwordEncoder());
        return p;
    }

    @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    ObjectProvider<ClientRegistrationRepository> clients) throws Exception {
        http
            // CSRF: 쿠키(XSRF-TOKEN)로 발급 → 프런트가 X-XSRF-TOKEN 헤더로 전송
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            // API는 401/403로 응답(리다이렉트 방지)
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                // 정적/헬스/Swagger
                .requestMatchers(HttpMethod.GET,
                    "/", "/index.html", "/static/**", "/assets/**",
                    "/favicon.ico", "/manifest.json",
                    "/actuator/health", "/actuator/info",
                    "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
                ).permitAll()
                // 회원가입/로그인 공개(※ CSRF 헤더는 필요)
                .requestMatchers(HttpMethod.POST,
                		"/api/auth/login", "/api/auth/register",
                		"/rag/chat", "/normal-chat"
                		).permitAll()
                // FastAPI 프록시 공개로 둘 경우(테스트/퍼블릭용); 보호하려면 이 줄 삭제
                .requestMatchers("/api/chat/**").permitAll()
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            // 세션 로그아웃
            .logout(l -> l
                .logoutUrl("/api/auth/logout")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpServletResponse.SC_OK))
            );

        // OAuth2는 등록정보 있을 때만 활성화(조건부)
        ClientRegistrationRepository repo = clients.getIfAvailable();
        if (repo != null) {
            http
              .oauth2Login(oauth -> oauth
                  .userInfoEndpoint(u -> u.userService(customOAuth2UserService))
                  .successHandler(oAuth2LoginSuccessHandler)
              )
              .oauth2Client(Customizer.withDefaults());
        }

        return http.build();
    }
}
