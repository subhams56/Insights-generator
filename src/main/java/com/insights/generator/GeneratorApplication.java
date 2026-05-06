package com.insights.generator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// CHANGE THIS: javax.sql.DataSource -> jakarta.sql.DataSource
import javax.sql.DataSource;
import java.sql.Connection;

@SpringBootApplication
@EnableScheduling
public class GeneratorApplication implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(GeneratorApplication.class);
    private final DataSource dataSource;

    public GeneratorApplication(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static void main(String[] args) {
        SpringApplication.run(GeneratorApplication.class, args);
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Checking database connection...");
        try (Connection connection = dataSource.getConnection()) {
            // connection.getCatalog() returns the DB name (insightsDb)
            String dbName = connection.getCatalog();
            logger.info("Successfully connected to Database: {}", dbName);
            logger.info("Database Product: {}", connection.getMetaData().getDatabaseProductName());
        } catch (Exception e) {
            logger.error("CRITICAL: Failed to connect to the database on startup!", e);
        }
    }
}