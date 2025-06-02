package com.aupma.codegen.graphql;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import java.util.List;

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

            // Register generator source set
            SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
            SourceSet generatorSourceSet = sourceSets.create("generator");

            ConfigurationContainer configurations = project.getConfigurations();
            configurations.getByName("generatorImplementation")
                    .extendsFrom(configurations.getByName("implementation"));

            // Add a task using a generator classpath
            project.getTasks().register(
                    "generateDgsFetchers", JavaExec.class, task -> {
                        task.setGroup("dgsFetcherGen");
                        task.setDescription("Generate DGS fetcher interfaces from GraphQL schema files");

                        task.getMainClass().set("com.aupma.codegen.graphql.GraphQLToDgsProcessor");
                        task.setClasspath(generatorSourceSet.getRuntimeClasspath());

                        task.getArgumentProviders().add(() -> List.of(
                                "--schemaDir=" + config.getSchemaDir(),
                                "--outputDir=" + config.getOutputDir(),
                                "--packageName=" + config.getPackageName()
                        ));
                    }
            );

            // Wire task before compileJava instead of build
            project.getTasks().named("compileJava").configure(compileTask ->
                    compileTask.dependsOn("generateDgsFetchers")
            );

            // Also add generated dir to the main sourceSet
            SourceSet mainSourceSet = sourceSets.getByName("main");
            mainSourceSet.getJava().srcDir(config.getOutputDir());
        });
    }

}

