package com.findus.backend.dev.config;

import com.findus.backend.dev.service.DummyDataService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
@ConditionalOnProperty(name = {"app.dummy.enabled", "app.dummy.startup-enabled"}, havingValue = "true")
public class DummyDataStartupRunner implements ApplicationRunner {
    private final DummyDataService service;
    public DummyDataStartupRunner(DummyDataService service) { this.service = service; }
    @Override
    public void run(ApplicationArguments args) { service.refreshStartup(); }
}
