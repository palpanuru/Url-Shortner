package com.urlshortener.security;

import com.urlshortener.web.ProblemWriter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Explicit filter ordering: authenticate first, then rate-limit by the authenticated client. */
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(SecurityProperties props,
            ProblemWriter problems) {
        var bean = new FilterRegistrationBean<>(new ApiKeyAuthFilter(props, problems));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimiter limiter,
            RateLimitProperties props, ProblemWriter problems) {
        var bean = new FilterRegistrationBean<>(new RateLimitFilter(limiter, props, problems));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return bean;
    }
}
