package org.mobilecms.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
class MediaResourceConfigurationTest {

    private static final Path ROOT = createRoot();

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("mobilecms.root-dir", () -> ROOT.toString());
        registry.add("mobilecms.conf-file", () -> ROOT.resolve("private/conf/conf.json").toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesMediaDirectoryUnderMediaContext() throws Exception {
        Files.writeString(ROOT.resolve("www/media/test.txt"), "hello-from-media");

        mockMvc.perform(get("/media/test.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string("hello-from-media"));
    }

    private static Path createRoot() {
        try {
            Path root = Files.createTempDirectory("mobilecms-media");
            Path mediaDir = root.resolve("www/media");
            Path privateDir = root.resolve("private/conf");
            Files.createDirectories(mediaDir);
            Files.createDirectories(privateDir);

            ObjectMapper mapper = new ObjectMapper();
            ObjectNode conf = mapper.createObjectNode();
            conf.put("publicdir", "www/public");
            conf.put("privatedir", "private");
            conf.put("media", "media");
            conf.put("jwt", "php-jwt");
            conf.put("enablemail", "false");
            conf.putArray("thumbnailsizes").add(300);
            conf.putArray("fileextensions").add("png");
            conf.putArray("mimetypes").add("image/png");
            conf.put("uploadmaxfilesize", "1MB");
            mapper.writerWithDefaultPrettyPrinter().writeValue(privateDir.resolve("conf.json").toFile(), conf);
            return root;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to initialize temp media root", e);
        }
    }
}
