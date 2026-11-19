package com.bbcc.kidly.boundedcontext.notification.out;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.AbstractDependsOnBeanFactoryPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 발송 실행기(applicationTaskExecutor)가 JPA(EntityManagerFactory)에 의존하게 해, 종료할 때 실행기가 먼저 닫히게 한다.
 * 종료 대기(await-termination)를 켜면 실행기는 빈을 소멸할 때 맡은 발송을 기다리는데, 이 순서가 없으면 JPA가 먼저 닫혀
 * 기다리는 동안 발송 결과를 기록하지 못한다. 빈은 자기가 의존하는 빈보다 먼저 소멸한다.
 */
@Component
class NotificationSendExecutorShutdownOrder extends AbstractDependsOnBeanFactoryPostProcessor {

    NotificationSendExecutorShutdownOrder() {
        super(ThreadPoolTaskExecutor.class, EntityManagerFactory.class);
    }
}
