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

    private static final Map<String, String> directiveToAnnotation = Map.ofEntries(
            Map.entry("AssertFalse", "AssertFalse"),
            Map.entry("AssertTrue", "AssertTrue"),
            Map.entry("DecimalMax", "DecimalMax"),
            Map.entry("DecimalMin", "DecimalMin"),
            Map.entry("Digits", "Digits"),
            Map.entry("Email", "Email"),
            Map.entry("Future", "Future"),
            Map.entry("FutureOrPresent", "FutureOrPresent"),
            Map.entry("Max", "Max"),
            Map.entry("Min", "Min"),
            Map.entry("Negative", "Negative"),
            Map.entry("NegativeOrZero", "NegativeOrZero"),
            Map.entry("NotBlank", "NotBlank"),
            Map.entry("NotEmpty", "NotEmpty"),
            Map.entry("Null", "Null"),
            Map.entry("Past", "Past"),
            Map.entry("PastOrPresent", "PastOrPresent"),
            Map.entry("Pattern", "Pattern"),
            Map.entry("Positive", "Positive"),
            Map.entry("PositiveOrZero", "PositiveOrZero"),
            Map.entry("Size", "Size")
    );

    private static void addValidationAnnotations(ParameterSpec.Builder paramBuilder, List<Directive> directives) {
        for (Directive directive : directives) {
            String dirName = directive.getName();
            if (!directiveToAnnotation.containsKey(dirName)) continue;
            String annName = directiveToAnnotation.get(dirName);
            ClassName annClass = ClassName.get("jakarta.validation.constraints", annName);

            AnnotationSpec.Builder annBuilder = AnnotationSpec.builder(annClass);

            // Handle annotation parameters from directive arguments
            for (Argument arg : directive.getArguments()) {
                String paramName = arg.getName();
                Value<?> val = arg.getValue();
                // Support common types: StringValue, IntValue, BooleanValue
                if (val instanceof StringValue s) {
                    annBuilder.addMember(paramName, "$S", s.getValue());
                } else if (val instanceof IntValue i) {
                    annBuilder.addMember(paramName, "$L", i.getValue());
                } else if (val instanceof BooleanValue b) {
                    annBuilder.addMember(paramName, "$L", b.isValue());
                }
            }

            paramBuilder.addAnnotation(annBuilder.build());
        }
    }

    private static MethodSpec generateMethod(FieldDefinition field, String annotation) {
        MethodSpec.Builder builder = MethodSpec.methodBuilder(field.getName())
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addAnnotation(ClassName.get("com.netflix.graphql.dgs", annotation));

        List<InputValueDefinition> inputValueDefs = field.getInputValueDefinitions();
        if (inputValueDefs != null && !inputValueDefs.isEmpty()) {
            for (InputValueDefinition input : inputValueDefs) {
                String argName = input.getName();
                Type<?> gqlType = input.getType();
                String rawType = unwrapTypeName(gqlType);

                TypeName type = isPrimitive(rawType)
                        ? mapPrimitive(rawType)
                        : ClassName.get("com.netflix.dgs.codegen.generated.types", rawType);

                ParameterSpec.Builder paramBuilder = ParameterSpec.builder(type, argName);

                if (gqlType instanceof NonNullType) {
                    paramBuilder.addAnnotation(ClassName.get("jakarta.validation.constraints", "NotNull"));
                }

                addValidationAnnotations(paramBuilder, input.getDirectives());

                paramBuilder.addAnnotation(ClassName.get("com.netflix.graphql.dgs", "InputArgument"));

                builder.addParameter(paramBuilder.build());
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
