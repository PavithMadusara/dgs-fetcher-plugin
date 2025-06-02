package com.aupma.codegen.dgs;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import java.util.List;
import java.util.ArrayList;

public class GraphqlToDgsPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getExtensions().create("dgsCodegen", DgsFetcherGenExtension.class);

        // Add task to initialize base GraphQL schema file
        project.getTasks().register(
                "initDgsSchema", task -> {
                    task.setGroup("dgsCodegen");
                    task.setDescription("Create a base GraphQL schema file");

                    task.doLast(_ -> {
                        DgsFetcherGenExtension config = project.getExtensions().getByType(DgsFetcherGenExtension.class);
                        String schemaDir = config.getSchemaDir();
                        java.io.File schemaDirFile = new java.io.File(schemaDir);

                        if (!schemaDirFile.exists()) {
                            schemaDirFile.mkdirs();
                        }

                        java.io.File baseSchemaFile = new java.io.File(schemaDirFile, "codegen.graphqls");

                        if (!baseSchemaFile.exists()) {
                            try {
                                // Read template from resources
                                java.io.InputStream templateStream = getClass().getClassLoader()
                                        .getResourceAsStream("codegen.graphqls.template");
                                if (templateStream == null) {
                                    project.getLogger().error("Could not find template file: codegen.graphqls");
                                    return;
                                }

                                String templateContent = new String(
                                        templateStream.readAllBytes(),
                                        java.nio.charset.StandardCharsets.UTF_8
                                );
                                templateStream.close();

                                // Write template content to target file
                                java.io.FileWriter writer = new java.io.FileWriter(baseSchemaFile);
                                writer.write(templateContent);
                                writer.close();
                                project.getLogger()
                                        .lifecycle("Created base GraphQL schema file at: " + baseSchemaFile.getAbsolutePath());
                            } catch (java.io.IOException e) {
                                project.getLogger().error("Failed to create base GraphQL schema file", e);
                            }
                        } else {
                            project.getLogger()
                                    .lifecycle("Base GraphQL schema file already exists at: " + baseSchemaFile.getAbsolutePath());
                        }
                    });
                }
        );

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
                    "generateDgs", JavaExec.class, task -> {
                        task.setGroup("dgsCodegen");
                        task.setDescription("Generate DGS java code from GraphQL schema files");

                        task.getMainClass().set("com.aupma.codegen.dgs.GraphQLToDgsProcessor");
                        task.setClasspath(generatorSourceSet.getRuntimeClasspath());

                        task.getArgumentProviders().add(() -> {
                            List<String> args = new ArrayList<>();
                            args.add("--schemaDir=" + config.getSchemaDir());
                            args.add("--outputDir=" + config.getOutputDir());
                            args.add("--packageName=" + config.getPackageName());

                            if (config.getExcludeFiles() != null && config.getExcludeFiles().length > 0) {
                                args.add("--excludeFiles=" + String.join(",", config.getExcludeFiles()));
                            }

                            return args;
                        });
                    }
            );

            // Wire task before compileJava instead of build
            project.getTasks().named("compileJava").configure(compileTask ->
                    compileTask.dependsOn("generateDgs")
            );

            // Also add generated dir to the main sourceSet
            SourceSet mainSourceSet = sourceSets.getByName("main");
            mainSourceSet.getJava().srcDir(config.getOutputDir());
        });
    }

}

