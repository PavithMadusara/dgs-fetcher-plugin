# DGS Fetcher Plugin

[![Maven Central](https://img.shields.io/maven-central/v/com.aupma.codegen/dgs-fetcher-plugin.svg)](https://search.maven.org/artifact/com.aupma.codegen/dgs-fetcher-plugin)
[![](https://jitpack.io/v/PavithMadusara/dgs-fetcher-plugin.svg)](https://jitpack.io/#PavithMadusara/dgs-fetcher-plugin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## Overview

An extension for Netflix DGS Codegen that generates interfaces for GraphQL queries and mutations. This plugin simplifies development with the Netflix DGS Framework by automatically generating typesafe Java interfaces from your GraphQL schema files.

## Features

- Creates a fetcher interface per .graphqls file in your schema folder
- Automatically handles both Query and Mutation types
- Generates proper method signatures with correct return types
- Adds appropriate DGS annotations
- Properly maps GraphQL types to Java types

## Getting Started

### Add the plugin to your project

```groovy
plugins {
    id 'com.aupma.codegen.dgs-fetcher-plugin' version '0.1.1'
}
```

### Configure the plugin

```groovy
dgsFetcherGen {
    // Directory containing GraphQL schema files (*.graphqls)
    schemaDir = "src/main/resources/schema" 

    // Directory where generated Java files will be written
    outputDir = "build/generated/sources/dgs-codegen" 

    // Base package name for generated Java code
    packageName = "com.example.graphql.fetchers"
}
```

## Schema Organization Recommendation

For the best experience with the IntelliJ DGS plugin and code navigation, it's recommended to:

1. Create a base.graphqls file with empty type declarations:
   ```graphql
   type Query {
   }

   type Mutation {
   }
   ```

2. In other schema files, use extension syntax:
   ```graphql
   extend type Query {
       // your queries here
   }

   extend type Mutation {
       // your mutations here
   }
   ```

## Example

Given a GraphQL schema file `user.graphqls` containing:

```graphql
extend type Query {
    user(id: ID!): User
    searchUsers(name: String, age: Int, active: Boolean = true): [User!]!
    allUsers: [User!]!
}

extend type Mutation {
    createUser(input: UserCreateInput!): User!
    updateUser(id: ID!, input: UserUpdateInput!): User
    deleteUser(id: ID!): Boolean!
}

type User {
    id: ID!
    username: String!
    email: String!
    age: Int
    isActive: Boolean!
    friends: [User!]!
    role: UserRole!
}

input UserCreateInput {
    username: String!
    email: String!
    age: Int
    role: UserRole = USER
}

input UserUpdateInput {
    username: String
    email: String
    age: Int
    isActive: Boolean
    role: UserRole
}

enum UserRole {
    ADMIN
    USER
    GUEST
}

interface Person {
    id: ID!
    name: String!
}

union SearchResult = User | Tenant

directive @deprecated(reason: String = "No longer supported") on FIELD_DEFINITION | ENUM_VALUE
```

The plugin will generate:

```java
package com.netflix.dgs.codegen.generated.fetchers;

import com.netflix.dgs.codegen.generated.types.User;
import com.netflix.dgs.codegen.generated.types.UserCreateInput;
import com.netflix.dgs.codegen.generated.types.UserUpdateInput;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import graphql.schema.DataFetchingEnvironment;
import java.lang.String;

@DgsComponent
public interface UserFetcher {
  @DgsQuery
  User user(@InputArgument String id, DataFetchingEnvironment dfe);

  @DgsQuery
  User searchUsers(@InputArgument String name, @InputArgument int age,
      @InputArgument boolean active, DataFetchingEnvironment dfe);

  @DgsQuery
  User allUsers(DataFetchingEnvironment dfe);

  @DgsMutation
  User createUser(@InputArgument UserCreateInput input, DataFetchingEnvironment dfe);

  @DgsMutation
  User updateUser(@InputArgument String id, @InputArgument UserUpdateInput input,
      DataFetchingEnvironment dfe);

  @DgsMutation
  boolean deleteUser(@InputArgument String id, DataFetchingEnvironment dfe);
}
```

## Usage

You can implement this interface in your service class or anywhere you want:

```java
@Service
public class UserService implements UserFetcher {
    // Implement the methods defined in the interface
    @Override
    public User user(String id, DataFetchingEnvironment dfe) {
        // Your implementation here
    }

    // ... other method implementations
}
```

## Building from Source

```bash
./gradlew build
```

## Using from JitPack

To use this plugin from GitHub Packages, add the following to your `settings.gradle`:

```groovy
dependencyResolutionManagement {
   repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
   repositories {
      mavenCentral()
      maven { url = 'https://jitpack.io' }
   }
}
```

on `build.gradle`
```groovy
buildscript {
    repositories {
        mavenCentral()
        maven { url = 'https://jitpack.io' }
    }
    dependencies {
        classpath 'com.github.pavithmadusara:dgs-fetcher-plugin:TAG'
    }
}
plugins {//...}
apply plugin: 'com.aupma.codegen.dgs-fetcher-plugin'
dependencies {
   implementation 'com.github.pavithmadsuara:dgs-fetcher-plugin:TAG'
}
```

## License

This project is licensed under the Apache License 2.0
