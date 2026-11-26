package com.nl2db.gateway.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterRegistration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration bean that registers the {@link CorrelationIdFilter} as a
 * Spring {@code FilterRegistrationBean}.
 * <p>
 * The filter is mapped to "/*" to ensure it processes all inbound requests.
 * Order is set to 0 to ensure it runs early in the filter chain.
 */
@Configuration
public class CorrelationIdFilterConfig {

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter(
            CorrelationIdFilter correlationIdFilter) {

        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(correlationIdFilter);
        registration.setOrder(0);
        registration.setFilter(correlationIdFilter);
        registration.addUrlPatterns("/*");
        return registration;
    }
}