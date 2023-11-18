package xyz.monado.subs;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CaseInsensitiveEnumConverterFactory caseInsensitiveEnumConverterFactory;
    private final LoggingInterceptor loggingInterceptor;

    public WebConfig(CaseInsensitiveEnumConverterFactory caseInsensitiveEnumConverterFactory, LoggingInterceptor loggingInterceptor) {
        this.caseInsensitiveEnumConverterFactory = caseInsensitiveEnumConverterFactory;
        this.loggingInterceptor = loggingInterceptor;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(caseInsensitiveEnumConverterFactory);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loggingInterceptor);
    }
}
