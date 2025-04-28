package top.voidc.service;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Service;
import top.voidc.config.ComponentsConfig;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class ComponentsService {

    private final ResourceLoader resourceLoader;
    private final Map<String, String> Components;

    public ComponentsService(ResourceLoader resourceLoader, ComponentsConfig config) {
        this.resourceLoader = resourceLoader;
        this.Components = config.getComponents();
    }

    /**
     * 获取指定目录下的文件列表及注释
     */
    public Map<String, String> getFilesWithComments(String dir) throws IOException {
        Map<String, String> files = new HashMap<>();
        // 加载 static/files 下的资源
        Resource[] resources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader)
                .getResources("classpath:static/" + dir + "/*");
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            // 剥离文件后缀（处理无后缀的情况）
            if (filename != null) {
                filename = filename.contains(".") ?
                        filename.substring(0, filename.lastIndexOf('.')) :
                        filename;
            }
            // 从配置中获取注释，若无则返回空字符串
            String comment = Components.getOrDefault(filename, filename);

            files.put(filename, comment);
        }
        return files;
    }
}
