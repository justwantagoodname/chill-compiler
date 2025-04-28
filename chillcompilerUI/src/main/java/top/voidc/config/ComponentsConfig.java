package top.voidc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "file") // 绑定前缀为 file
public class ComponentsConfig {

    private Map<String, String> components; // 自动映射 file.components.xxx

    public Map<String, String> getComponents() {
        return components;
    }

    public void setComponents(Map<String, String> components) {
        this.components = components;
    }

}