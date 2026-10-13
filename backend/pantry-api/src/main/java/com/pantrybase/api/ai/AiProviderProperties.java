package com.pantrybase.api.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.ai")
public class AiProviderProperties {

    @Valid
    private List<Provider> providers = List.of();

    public List<Provider> getProviders() {
        return providers;
    }

    public void setProviders(List<Provider> providers) {
        this.providers = providers;
    }

    @Validated
    public static class Provider {

        @NotNull
        private Type type;

        @NotNull
        @NotEmpty
        private String model;

        public Type getType() {
            return type;
        }

        public void setType(Type type) {
            this.type = type;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public enum Type {
            OLLAMA
        }
    }
}