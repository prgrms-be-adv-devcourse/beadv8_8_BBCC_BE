package com.bbcc.kidly;

import org.springframework.boot.SpringApplication;

// Docker만 있으면 DB 없이 앱을 띄운다: ./gradlew bootTestRun
public class TestKidlyApplication {

    public static void main(String[] args) {
        SpringApplication.from(KidlyApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
