package com.smartteach;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

/** Real SQL Server for integration tests, same image as docker-compose. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MSSQLServerContainer sqlServerContainer() {
        return new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
                .acceptLicense();
    }
}
