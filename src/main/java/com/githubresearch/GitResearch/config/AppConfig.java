package com.githubresearch.GitResearch.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private static final Properties props = new Properties();
    
    static {
        try (InputStream input = AppConfig.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new RuntimeException("Unable to find config.properties");
            }
            props.load(input);
        } catch (IOException ex) {
            throw new RuntimeException("Error loading configuration", ex);
        }
    }
    
    public static String getGitHubApiToken() {
        return props.getProperty("github.api.token");
    }
    
    public static String getGitHubApiUrl() {
        return props.getProperty("github.api.url");
    }
    
    public static int getServerPort() {
        return Integer.parseInt(props.getProperty("server.port", "8080"));
    }
}