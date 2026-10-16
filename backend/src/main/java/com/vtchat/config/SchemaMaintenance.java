package com.vtchat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(0)
public class SchemaMaintenance implements ApplicationRunner {

    private static final int NEEDED = 191;
    private final JdbcTemplate jdbc;

    public SchemaMaintenance(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer current = jdbc.queryForObject(
                    "select character_maximum_length from information_schema.columns "
                            + "where table_schema = database() and table_name = 'users' and column_name = 'profile_picture'",
                    Integer.class);
            if (current != null && current < NEEDED) {
                jdbc.execute("alter table users modify column profile_picture varchar(" + NEEDED + ") null");
                log.info("Widened users.profile_picture from {} to {} characters", current, NEEDED);
            }
        } catch (RuntimeException e) {
            log.warn("Could not check or widen users.profile_picture (run: ALTER TABLE users MODIFY profile_picture VARCHAR({}) NULL) category={}",
                    NEEDED, e.getClass().getSimpleName());
        }
    }
}
