package com.aupma.codegen.dgs;

/**
 * Extension for configuring the GraphQL to DGS fetcher generation.
 *
 * <p>This extension provides configuration options for the GraphqlToDgsPlugin,
 * allowing customization of input schema location, output directory, and target package name.</p>
 */
public class DgsFetcherGenExtension {
    private String schemaDir = "src/main/resources/schema";

    private String outputDir = "build/generated/sources/dgs-codegen";

    private String packageName = "com.aupma.codegen.dgs";

    private String[] excludeFiles = {"codegen.graphqls"};

    public String getSchemaDir() {
        return schemaDir;
    }

    public void setSchemaDir(String schemaDir) {
        this.schemaDir = schemaDir;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String[] getExcludeFiles() {
        return excludeFiles;
    }

    public void setExcludeFiles(String[] excludeFiles) {
        this.excludeFiles = excludeFiles;
    }
}
