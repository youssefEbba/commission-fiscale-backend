package mr.gov.finances.sgci.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class ApiPathCorsFilterConfig {

    @Bean
    public FilterRegistrationBean<ApiPathCorsFilter> apiPathCorsFilterRegistration(org.springframework.core.env.Environment environment) {
        FilterRegistrationBean<ApiPathCorsFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new ApiPathCorsFilter(CorsAllowedOrigins.pour(environment)));
        reg.addUrlPatterns("/api/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }
}
