package mr.gov.finances.sgci.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Enregistre le filtre CORS WebSocket avant la chaîne Spring Security.
 */
@Configuration
public class WsPathCorsFilterConfig {

    @Bean
    public FilterRegistrationBean<WsPathCorsFilter> wsPathCorsFilterRegistration() {
        FilterRegistrationBean<WsPathCorsFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new WsPathCorsFilter());
        reg.addUrlPatterns("/ws", "/ws/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }
}
