package xyz.monado.subs;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.stereotype.Component;

@Component
public final class CaseInsensitiveEnumConverterFactory
        implements ConverterFactory<String, Enum> {

    @Override
    public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
        return new CaseInsensitiveEnumConverter<>(targetType);
    }

    private static class CaseInsensitiveEnumConverter<T extends Enum> implements Converter<String, T> {

        private Class<T> enumType;

        public CaseInsensitiveEnumConverter(Class<T> enumType) {
            this.enumType = enumType;
        }

        public T convert(String source) {
            return (T) Enum.valueOf(this.enumType, source.toUpperCase());
        }
    }
}
