package com.bbcc.kidly.boundedcontext.auth.out;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AuthPasswordConfig {

    @Bean
    public PasswordEncoder authPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
