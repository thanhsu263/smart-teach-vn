package com.smartteach.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** With the "demo" profile, recreates the demo teacher's data on every start. */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
class DemoDataRunner implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final DemoData demoData;
    private final DemoProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        String password = properties.password();
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("The demo profile needs DEMO_PASSWORD (at least 8 characters)");
        }
        demoData.reset(properties.email(), password);
        log.info("Demo data recreated for {}", properties.email());
    }
}
