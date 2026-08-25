package com.loop.loop_backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// application.yaml 은 모든 값을 ${ENV} 로 받으므로 env 가 없는 테스트에서는 뜨지 않는다.
// src/test/resources/application-test.yaml 이 같은 키를 더미 값으로 덮어준다.
@SpringBootTest
@ActiveProfiles("test")
class LoopBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
