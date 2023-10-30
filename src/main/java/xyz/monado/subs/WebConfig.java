package xyz.monado.subs;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CaseInsensitiveEnumConverterFactory caseInsensitiveEnumConverterFactory;

    public WebConfig(CaseInsensitiveEnumConverterFactory caseInsensitiveEnumConverterFactory) {
        this.caseInsensitiveEnumConverterFactory = caseInsensitiveEnumConverterFactory;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(caseInsensitiveEnumConverterFactory);
    }
}
