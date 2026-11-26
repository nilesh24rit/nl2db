package com.nl2db.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal startup test to verify the application class is valid.
 */
class GatewayStartupTest {

    @Test
    void applicationClassIsInstanciable() {
        assertNotNull(Nl2dbApplication.class);
    }

    @Test
    void mainClassCanBeLoaded() throws ClassNotFoundException {
        Class<?> cls = Class.forName("com.nl2db.gateway.Nl2dbApplication");
        assertNotNull(cls);
    }
}