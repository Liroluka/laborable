package app.laborable.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, LaborableProperties props) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(Customizer.withDefaults())
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth -> auth.requestMatchers("/admin/**").hasRole("ADMIN").anyRequest().permitAll())
        .exceptionHandling(
            e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .addFilterBefore(
            new AdminApiKeyFilter(props.admin().apiKey()),
            UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  /** Read-only public API: cross-origin GET only from explicitly configured origins. */
  @Bean
  CorsConfigurationSource corsConfigurationSource(LaborableProperties props) {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    List<String> origins = props.cors().allowedOrigins();
    if (origins != null && !origins.isEmpty()) {
      CorsConfiguration config = new CorsConfiguration();
      config.setAllowedOrigins(origins);
      config.setAllowedMethods(List.of("GET", "HEAD", "OPTIONS"));
      config.setMaxAge(3600L);
      source.registerCorsConfiguration("/**", config);
    }
    return source;
  }

  /** No users exist; stops Boot from generating a default password. */
  @Bean
  UserDetailsService userDetailsService() {
    return new InMemoryUserDetailsManager();
  }
}
