package com.aupma.codegen.graphql;

/**
 * A processor that converts GraphQL schema files (*.graphqls) into Netflix DGS Framework
 * data fetcher interfaces. The processor handles both Query and Mutation types from GraphQL schemas.
 *
 * <p>This tool generates abstract interface definitions with appropriate DGS annotations that
 * developers can implement to create GraphQL resolvers compatible with the Netflix DGS Framework.</p>
 *
 * <p>Usage: java GraphQLToDgsProcessor --schemaDir=/path/to/schemas --outputDir=/path/to/output --packageName=com.example.package</p>
 */

import com.palantir.javapoet.*;
import com.palantir.javapoet.TypeName;
import graphql.language.*;
import graphql.parser.Parser;

import javax.lang.model.element.Modifier;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GraphQLToDgsProcessor {
    /**
     * Entry point for the GraphQL to DGS processor.
     *
     * @param args Command line arguments in the format "--key=value". Expected arguments:
     *             <ul>
     *             <li>--schemaDir: Directory containing GraphQL schema files (*.graphqls)</li>
     *             <li>--outputDir: Directory where generated Java files will be written</li>
     *             <li>--packageName: Base package name for generated Java code</li>
     *             </ul>
     * @throws IOException If there's an error reading schema files or writing output files
     */
    public static void main(String[] args) throws IOException {
        Map<String, String> config = Arrays.stream(args)
                .filter(arg -> arg.contains("="))
                .map(arg -> arg.split("=", 2))
                .collect(Collectors.toMap(kv -> kv[0].replace("--", ""), kv -> kv[1]));

        String schemaDir = config.get("schemaDir");
        String outputDir = config.get("outputDir");
        String basePackage = config.get("packageName");

        File schemaDirFile = new File(schemaDir);
        if (!schemaDirFile.exists() || !schemaDirFile.isDirectory()) {
            System.err.println("Schema directory does not exist: " + schemaDir);
            return;
        }

        Files.walk(Paths.get(schemaDir))
                .filter(Files::isRegularFile)
                .filter(f -> f.toString().endsWith(".graphqls"))
                .forEach(f -> processFile(f.toFile(), basePackage, outputDir));
    }

    /**
     * Processes a single GraphQL schema file and generates a corresponding DGS data fetcher interface.
     *
     * @param file        The GraphQL schema file to process
     * @param basePackage The base package name for the generated Java code
     * @param outputDir   The directory where the generated Java file will be written
     */
    private static void processFile(File file, String basePackage, String outputDir) {
        String raw = readFile(file);
        Document doc = Parser.parse(raw);
        String fetcherName = capitalize(file.getName().replace(".graphqls", "")) + "Fetcher";

        TypeSpec.Builder interfaceBuilder = TypeSpec.interfaceBuilder(fetcherName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("com.netflix.graphql.dgs", "DgsComponent"));

        List<Definition> definitions = doc.getDefinitions();
        for (Definition def : definitions) {
            if (def instanceof ObjectTypeDefinition objDef) {
                String annotation;
                if (objDef.getName().equals("Query")) annotation = "DgsQuery";
                else if (objDef.getName().equals("Mutation")) annotation = "DgsMutation";
                else continue;

                for (FieldDefinition field : objDef.getFieldDefinitions()) {
                    MethodSpec method = generateMethod(field, annotation);
                    interfaceBuilder.addMethod(method);
                }
            }
        }

        JavaFile javaFile = JavaFile.builder(basePackage, interfaceBuilder.build()).build();
        try {
            File outputPath = new File(outputDir);
            if (!outputPath.exists()) {
                outputPath.mkdirs();
            }
            javaFile.writeTo(Paths.get(outputDir));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Generates a method specification for a GraphQL field.
     *
     * @param field      The GraphQL field definition
     * @param annotation The DGS annotation to apply ("DgsQuery" or "DgsMutation")
     * @return A JavaPoet MethodSpec representing the generated method
     */
    private static MethodSpec generateMethod(FieldDefinition field, String annotation) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder(field.getName())
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addAnnotation(ClassName.get("com.netflix.graphql.dgs", annotation));

        List<InputValueDefinition> inputValueDefs = field.getInputValueDefinitions();
        if (inputValueDefs != null && !inputValueDefs.isEmpty()) {
            for (InputValueDefinition input : inputValueDefs) {
                String argName = input.getName();
                String rawType = unwrapTypeName(input.getType());

                TypeName type = isPrimitive(rawType)
                        ? mapPrimitive(rawType)
                        : ClassName.get("com.netflix.dgs.codegen.generated.types", rawType);

                builder.addParameter(ParameterSpec.builder(type, argName)
                        .addAnnotation(ClassName.get("com.netflix.graphql.dgs", "InputArgument"))
                        .build());
            }
        }

        builder.addParameter(ClassName.get("graphql.schema", "DataFetchingEnvironment"), "dfe");

        String returnType = unwrapTypeName(field.getType());
        TypeName returnJavaType = isPrimitive(returnType)
                ? mapPrimitive(returnType)
                : ClassName.get("com.netflix.dgs.codegen.generated.types", returnType);

        builder.returns(returnJavaType);
        return builder.build();
    }


    /**
     * Determines if a GraphQL type is a primitive type.
     *
     * @param type The GraphQL type name
     * @return true if the type is a GraphQL primitive (String, ID, Int, Float, Boolean)
     */
    private static boolean isPrimitive(String type) {
        return Set.of("String", "ID", "Int", "Float", "Boolean").contains(type);
    }

    /**
     * Extracts the base type name from a GraphQL type, handling non-null and list wrappers.
     *
     * @param type The GraphQL type
     * @return The unwrapped type name as a string
     * @throws IllegalArgumentException if the type is null or unrecognized
     */
    private static String unwrapTypeName(Type<?> type) {
        return switch (type) {
            case NonNullType nonNull -> unwrapTypeName(nonNull.getType());
            case ListType listType -> unwrapTypeName(listType.getType());
            case graphql.language.TypeName typeName -> typeName.getName();
            case null, default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }

    /**
     * Maps GraphQL primitive types to their corresponding Java types.
     *
     * @param type The GraphQL primitive type name
     * @return The corresponding Java TypeName
     */
    private static TypeName mapPrimitive(String type) {
        return switch (type) {
            case "ID", "String" -> ClassName.get(String.class);
            case "Int" -> TypeName.INT;
            case "Float" -> TypeName.DOUBLE;
            case "Boolean" -> TypeName.BOOLEAN;
            default -> ClassName.get(String.class);
        };
    }

    /**
     * Capitalizes the first letter of a string.
     *
     * @param name The string to capitalize
     * @return The capitalized string
     */
    private static String capitalize(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    /**
     * Reads the contents of a file as a string.
     *
     * @param file The file to read
     * @return The file contents as a string
     * @throws RuntimeException if there's an error reading the file
     */
    private static String readFile(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
