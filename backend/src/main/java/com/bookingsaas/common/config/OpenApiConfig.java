package com.bookingsaas.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.jackson.TypeNameResolver;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalTime;

/**
 * Makes the API description match what the API actually sends, since the
 * frontend's types are generated from it.
 */
@Configuration
public class OpenApiConfig {

    static {
        // Jackson writes a LocalTime as "09:00:00"; springdoc would otherwise describe it as an object.
        SpringDocUtils.getConfig().replaceWithSchema(LocalTime.class,
                new StringSchema().format("time").example("09:00:00"));
    }

    /**
     * Schemas are named by simple class name, and several DTO files nest a
     * record of the same name ({@code Option}, {@code ChoosableRequirement},
     * {@code QuoteRequest} …). Without this, one silently replaces the other in
     * the description. A nested record is named after its enclosing class
     * too: {@code PublicServiceResponseOption}, {@code ConversationDtosOption}.
     */
    @Bean
    public ModelResolver nestedRecordNames(ObjectMapper objectMapper) {
        return new ModelResolver(objectMapper, new NestedTypeNameResolver());
    }

    static final class NestedTypeNameResolver extends TypeNameResolver {

        @Override
        protected String getNameOfClass(Class<?> cls) {
            String name = super.getNameOfClass(cls);
            Class<?> enclosing = cls.getEnclosingClass();
            return enclosing == null || cls.isEnum() ? name : enclosing.getSimpleName() + name;
        }
    }
}
