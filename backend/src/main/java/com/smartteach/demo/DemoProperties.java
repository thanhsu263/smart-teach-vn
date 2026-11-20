package com.smartteach.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under {@code app.demo}; used only with the "demo" profile. */
@ConfigurationProperties("app.demo")
public record DemoProperties(String email, String password) {
}
