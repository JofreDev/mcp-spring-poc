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
        private String generatedLocation;
        private String effectiveLocation;
        private String generatedFile = "mcp.generated.yaml";
        private String effectiveFile = "mcp.effective.yaml";
        private String lockFile = "mcp.lock.yaml";
        private String lockMode = "off";
        private String overridesMode = "strict";

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

        public String getGeneratedLocation() {
            return generatedLocation;
        }

        public void setGeneratedLocation(String generatedLocation) {
            this.generatedLocation = generatedLocation;
        }

        public String getEffectiveLocation() {
            return effectiveLocation;
        }

        public void setEffectiveLocation(String effectiveLocation) {
            this.effectiveLocation = effectiveLocation;
        }

        public String getGeneratedFile() {
            return generatedFile;
        }

        public void setGeneratedFile(String generatedFile) {
            this.generatedFile = generatedFile;
        }

        public String getEffectiveFile() {
            return effectiveFile;
        }

        public void setEffectiveFile(String effectiveFile) {
            this.effectiveFile = effectiveFile;
        }

        public String getLockFile() {
            return lockFile;
        }

        public void setLockFile(String lockFile) {
            this.lockFile = lockFile;
        }

        public String getLockMode() {
            return lockMode;
        }

        public void setLockMode(String lockMode) {
            this.lockMode = lockMode;
        }

        public String getOverridesMode() {
            return overridesMode;
        }

        public void setOverridesMode(String overridesMode) {
            this.overridesMode = overridesMode;
        }
    }
}
