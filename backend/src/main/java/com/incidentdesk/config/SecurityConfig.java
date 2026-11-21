package main.java.com.incidentdesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Disable CSRF for REST API testing
            .cors(cors -> {}) // Enable CORS (respects your @CrossOrigin annotations)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/incidents/**").permitAll() // Allow open access to incident endpoints for now
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
