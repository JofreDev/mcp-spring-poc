package com.example.mcpdemo.manifest.io;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppManifestProperties {

    private final OpenApi openapi = new OpenApi();
    private final Overrides overrides = new Overrides();
    private final Manifest manifest = new Manifest();

    public OpenApi getOpenapi() {
        return openapi;
    }

    public Overrides getOverrides() {
        return overrides;
    }

    public Manifest getManifest() {
        return manifest;
    }

    public static class OpenApi {
        private String location;

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }
    }

    public static class Overrides {
        private String location;

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }
    }

    public static class Manifest {
        private boolean exportOnStartup;
        private String exportDir = "build/mcp-manifests";

        public boolean isExportOnStartup() {
            return exportOnStartup;
        }

        public void setExportOnStartup(boolean exportOnStartup) {
            this.exportOnStartup = exportOnStartup;
        }

        public String getExportDir() {
            return exportDir;
        }

        public void setExportDir(String exportDir) {
            this.exportDir = exportDir;
        }
    }
}
