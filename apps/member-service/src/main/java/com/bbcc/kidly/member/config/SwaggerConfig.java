package com.bbcc.kidly.member.config;

import com.bbcc.kidly.member.support.security.LoginAdmin;
import com.bbcc.kidly.member.support.security.LoginChild;
import com.bbcc.kidly.member.support.security.LoginMember;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(LoginMember.class, LoginAdmin.class, LoginChild.class);
    }

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Kidly API")
                .description("자녀 맞춤 키즈 패션 이커머스")
                .version("v1"))
            .components(new Components()
                .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
