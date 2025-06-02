package com.aupma.codegen.dgs;

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

    private static String basePackage;

    public static void main(String[] args) throws IOException {
        Map<String, String> config = Arrays.stream(args)
                .filter(arg -> arg.contains("="))
                .map(arg -> arg.split("=", 2))
                .collect(Collectors.toMap(kv -> kv[0].replace("--", ""), kv -> kv[1]));

        String schemaDir = config.get("schemaDir");
        String outputDir = config.get("outputDir");
        basePackage = config.get("packageName");
        String excludeFilesStr = config.get("excludeFiles");
        Set<String> excludeSet;

        if (excludeFilesStr != null && !excludeFilesStr.isEmpty()) {
            excludeSet = Arrays.stream(excludeFilesStr.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
        } else {
            excludeSet = Set.of("codegen.graphqls");
        }

        System.out.println("Excluding files: " + excludeSet);

        File schemaDirFile = new File(schemaDir);
        if (!schemaDirFile.exists() || !schemaDirFile.isDirectory()) {
            System.err.println("Schema directory does not exist: " + schemaDir);
            return;
        }

        Files.walk(Paths.get(schemaDir))
                .filter(Files::isRegularFile)
                .filter(f -> f.toString().endsWith(".graphqls"))
                .filter(f -> !excludeSet.contains(f.getFileName().toString()))
                .forEach(f -> processFile(f.toFile(), outputDir));
    }

    private static void generateClassForType(String packageName, String className, Map<String, String> fields, String outputDir) {
        generateClassForType(packageName, className, fields, outputDir, null);
    }

    private static void generateClassForType(String packageName, String className, Map<String, String> fields, String outputDir, List<InputValueDefinition> inputValueDefs) {
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("lombok", "Data"))
                .addAnnotation(ClassName.get("lombok", "NoArgsConstructor"))
                .addAnnotation(ClassName.get("lombok", "AllArgsConstructor"));

        for (Map.Entry<String, String> entry : fields.entrySet()) {
            TypeName typeName = mapGraphQLTypeToTypeName(entry.getValue());
            FieldSpec.Builder fieldBuilder = FieldSpec.builder(typeName, entry.getKey(), Modifier.PRIVATE);

            // Add validation annotations if inputValueDefs are provided
            if (inputValueDefs != null) {
                InputValueDefinition inputValueDef = inputValueDefs.stream()
                        .filter(ivd -> ivd.getName().equals(entry.getKey()))
                        .findFirst()
                        .orElse(null);

                if (inputValueDef != null) {
                    // Add @NotNull for NonNullType fields
                    if (inputValueDef.getType() instanceof NonNullType) {
                        fieldBuilder.addAnnotation(ClassName.get("jakarta.validation.constraints", "NotNull"));
                    }

                    // Add validation annotations from directives
                    addValidationAnnotationsToField(fieldBuilder, inputValueDef.getDirectives());
                }
            }

            classBuilder.addField(fieldBuilder.build());
        }

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build()).build();
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

    private static TypeName mapGraphQLTypeToTypeName(String type) {
        return switch (type) {
            case "String", "ID" -> ClassName.get(String.class);
            case "Int" -> TypeName.INT;
            case "Float" -> TypeName.DOUBLE;
            case "Boolean" -> TypeName.BOOLEAN;
            default -> ClassName.get(basePackage + ".types", type);
        };
    }


    private static void processFile(File file, String outputDir) {
        String raw = readFile(file);
        Document doc = Parser.parse(raw);

        doc.getDefinitions().stream()
                .filter(def -> def instanceof ObjectTypeDefinition)
                .map(def -> (ObjectTypeDefinition) def)
                .filter(obj -> !obj.getName().equals("Query") && !obj.getName().equals("Mutation"))
                .forEach(obj -> {
                    Map<String, String> fields = obj.getFieldDefinitions().stream()
                            .collect(Collectors.toMap(
                                    FieldDefinition::getName,
                                    fd -> mapGraphQLTypeToJava(fd.getType())
                            ));
                    generateClassForType(basePackage + ".types", obj.getName(), fields, outputDir);
                });

        doc.getDefinitions().stream()
                .filter(def -> def instanceof InputObjectTypeDefinition)
                .map(def -> (InputObjectTypeDefinition) def)
                .forEach(inputObj -> {
                    Map<String, String> fields = inputObj.getInputValueDefinitions().stream()
                            .collect(Collectors.toMap(
                                    InputValueDefinition::getName,
                                    ivd -> mapGraphQLTypeToJava(ivd.getType())
                            ));
                    generateClassForType(basePackage + ".types", inputObj.getName(), fields, outputDir, inputObj.getInputValueDefinitions());
                });

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

        JavaFile javaFile = JavaFile.builder(basePackage + ".fetchers", interfaceBuilder.build()).build();
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


    private static String mapGraphQLTypeToJava(Type<?> gqlType) {
        String rawType = unwrapTypeName(gqlType);
        return switch (rawType) {
            case "ID", "String" -> "String";
            case "Int" -> "int";
            case "Float" -> "double";
            case "Boolean" -> "boolean";
            default -> basePackage + ".types." + rawType;
        };
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
                        : ClassName.get(basePackage + ".types", rawType);

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
                : ClassName.get(basePackage + ".types", returnType);

        builder.returns(returnJavaType);
        return builder.build();
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

            for (Argument arg : directive.getArguments()) {
                String paramName = arg.getName();
                Value<?> val = arg.getValue();
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

    private static void addValidationAnnotationsToField(FieldSpec.Builder fieldBuilder, List<Directive> directives) {
        for (Directive directive : directives) {
            String dirName = directive.getName();
            if (!directiveToAnnotation.containsKey(dirName)) continue;
            String annName = directiveToAnnotation.get(dirName);
            ClassName annClass = ClassName.get("jakarta.validation.constraints", annName);

            AnnotationSpec.Builder annBuilder = AnnotationSpec.builder(annClass);

            for (Argument arg : directive.getArguments()) {
                String paramName = arg.getName();
                Value<?> val = arg.getValue();
                if (val instanceof StringValue s) {
                    annBuilder.addMember(paramName, "$S", s.getValue());
                } else if (val instanceof IntValue i) {
                    annBuilder.addMember(paramName, "$L", i.getValue());
                } else if (val instanceof BooleanValue b) {
                    annBuilder.addMember(paramName, "$L", b.isValue());
                }
            }

            fieldBuilder.addAnnotation(annBuilder.build());
        }
    }

    private static boolean isPrimitive(String type) {
        return Set.of("String", "ID", "Int", "Float", "Boolean").contains(type);
    }

    private static String unwrapTypeName(Type<?> type) {
        return switch (type) {
            case NonNullType nonNull -> unwrapTypeName(nonNull.getType());
            case ListType listType -> unwrapTypeName(listType.getType());
            case graphql.language.TypeName typeName -> typeName.getName();
            case null, default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }

    private static TypeName mapPrimitive(String type) {
        return switch (type) {
            case "ID", "String" -> ClassName.get(String.class);
            case "Int" -> TypeName.INT;
            case "Float" -> TypeName.DOUBLE;
            case "Boolean" -> TypeName.BOOLEAN;
            default -> ClassName.get(String.class);
        };
    }

    private static String capitalize(String name) {
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private static String readFile(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
