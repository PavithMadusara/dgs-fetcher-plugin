package com.aupma.codegen.graphql;

/**
 * Extension for configuring the GraphQL to DGS fetcher generation.
 *
 * <p>This extension provides configuration options for the GraphqlToDgsPlugin,
 * allowing customization of input schema location, output directory, and target package name.</p>
 */
public class DgsFetcherGenExtension {
    /**
     * Directory containing GraphQL schema files (*.graphqls).
     * The default value is "src/main/resources/schema".
     */
    private String schemaDir = "src/main/resources/schema";

    /**
     * Directory where generated Java files will be written.
     * The default value is "build/generated/sources/dgs-codegen".
     */
    private String outputDir = "build/generated/sources/dgs-codegen";

    /**
     * Base package name for generated Java code.
     * The default value is "com.netflix.dgs.codegen.generated.fetchers".
     */
    private String packageName = "com.netflix.dgs.codegen.generated.fetchers";

    /**
     * Gets the directory containing GraphQL schema files.
     *
     * @return The schema directory path
     */
    public String getSchemaDir() {
        return schemaDir;
    }

    /**
     * Sets the directory containing GraphQL schema files.
     *
     * @param schemaDir The schema directory path
     */
    public void setSchemaDir(String schemaDir) {
        this.schemaDir = schemaDir;
    }

    /**
     * Gets the directory where generated Java files will be written.
     *
     * @return The output directory path
     */
    public String getOutputDir() {
        return outputDir;
    }

    /**
     * Sets the directory where generated Java files will be written.
     *
     * @param outputDir The output directory path
     */
    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    /**
     * Gets the base package name for generated Java code.
     *
     * @return The package name
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * Sets the base package name for generated Java code.
     *
     * @param packageName The package name
     */
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }
}
