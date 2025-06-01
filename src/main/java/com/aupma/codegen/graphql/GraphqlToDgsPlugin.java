package com.aupma.codegen.graphql;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.JavaExec;

/**
 * A Gradle plugin that generates Netflix DGS Framework data fetcher interfaces from GraphQL schema files.
 *
 * <p>This plugin creates a 'generateDgsFetchers' task that processes GraphQL schema files
 * and generates Java interfaces with appropriate DGS annotations that can be implemented
 * to create GraphQL resolvers.</p>
 *
 * <p>Example usage in build.gradle:</p>
 * <pre>
 * plugins {
 *     id 'com.aupma.codegen.graphql-to-dgs'
 * }
 *
 * dgsFetcherGen {
 *     schemaDir = "src/main/resources/schema"
 *     outputDir = "build/generated/sources/dgs-codegen"
 *     packageName = "com.example.graphql.fetchers"
 * }
 * </pre>
 */
public class GraphqlToDgsPlugin implements Plugin<Project> {
    /**
     * Applies this plugin to the given project.
     *
     * <p>This method registers the 'dgsFetcherGen' extension and creates a 'generateDgsFetchers' task
     * that generates DGS fetcher interfaces from GraphQL schema files. The task is automatically
     * executed as part of the 'build' task.</p>
     *
     * @param project The project to which this plugin is applied
     */
    @Override
    public void apply(Project project) {
        project.getExtensions().create("dgsFetcherGen", DgsFetcherGenExtension.class);

        project.afterEvaluate(_ -> {
            DgsFetcherGenExtension config = project.getExtensions().getByType(DgsFetcherGenExtension.class);

            project.getTasks().register(
                    "generateDgsFetchers", JavaExec.class, task -> {
                        task.setGroup("dgsFetcherGen");
                        task.setDescription("Generate DGS fetcher interfaces from GraphQL schema files");

                        task.getMainClass().set("com.aupma.codegen.graphql.GraphQLToDgsProcessor");
                        JavaPluginExtension javaPluginExtension = project.getExtensions()
                                .findByType(JavaPluginExtension.class);
                        assert javaPluginExtension != null;
                        task.setClasspath(javaPluginExtension.getSourceSets().getByName("main").getRuntimeClasspath());

                        task.getArgumentProviders().add(() -> java.util.List.of(
                                "--schemaDir=" + config.getSchemaDir(),
                                "--outputDir=" + config.getOutputDir(),
                                "--packageName=" + config.getPackageName()
                        ));
                    }
            );
            project.getTasks().named("build").configure(buildTask -> buildTask.finalizedBy("generateDgsFetchers"));
        });
    }
}

