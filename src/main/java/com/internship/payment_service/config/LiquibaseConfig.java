package com.internship.payment_service.config;

import jakarta.annotation.PostConstruct;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "spring.liquibase", name = "enabled", havingValue = "true")
public class LiquibaseConfig {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${spring.liquibase.change-log}")
    private String changeLogPath;

    @PostConstruct
    public void init() throws LiquibaseException {
        try (Database database = DatabaseFactory.getInstance()
                .openDatabase(mongoUri + "?liquibase=true", null, null, null, null, null)) {

            Liquibase liquibase = new Liquibase(changeLogPath, new ClassLoaderResourceAccessor(), database);
            List<ChangeSet> changeSetsList = liquibase.listUnrunChangeSets(null, null);
            if (!changeSetsList.isEmpty()) {
                liquibase.update(new Contexts());
            }
        }
    }
}