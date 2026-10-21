package com.bcp.workshop.auth;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@Alternative
@Priority(1)
@ApplicationScoped
public class CharacterizationDatabase extends LegacyDatabase {
    @Override
    public UserRecord findByCredentials(String username, String password) {
        if ("logging-fixture-user".equals(username) && "logging-unique-password-fixture".equals(password)) {
            return new UserRecord(1001L, username, "Logging Fixture", "STUDENT");
        }
        if ("characterization-fixture".equals(password)) {
            if ("characterization-failure".equals(username)) {
                throw new IllegalStateException("Characterization database failure");
            }
            if ("characterization-failure-without-message".equals(username)) {
                throw new IllegalStateException();
            }
            if ("characterization-html-user".equals(username)) {
                return new UserRecord(1000L, username, "<Student> & \"Workshop\"", "STUDENT");
            }
        }
        return super.findByCredentials(username, password);
    }
}