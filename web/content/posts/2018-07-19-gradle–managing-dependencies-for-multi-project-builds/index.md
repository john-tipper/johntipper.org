---
title: Gradle – Managing Dependencies for Multi-Project Builds
author: John Tipper
date: 2018-07-19
hero: ./images/IMG_0814.jpeg
excerpt: How to organise dependency versions for dependencies shared across multiple projects within a Gradle multi-module project.
---

When defining dependencies in Gradle, you’ll end up with a list in your `build.gradle` file of Maven Group-Artifact-Version items that you want to import, together with when you want access to them (compile, runtime etc).  Here is an example, taken from [the docs](https://docs.gradle.org/current/userguide/dependency_management_for_java_projects.html#sec:setting_up_a_standard_build_script_java_tutorial):

```groovy
// within build.gradle
dependencies {
    implementation 'org.hibernate:hibernate-core:3.6.7.Final'
    api 'com.google.guava:guava:23.0'
    testImplementation 'junit:junit:4.+'
}
```
Now imagine that you have a multi-project build, perhaps as described in the [previous post here](/gradle-managing-dependencies-for-multi-project-builds).  Each project has its own dependencies, many of which are common.  How is best to deal with this?

If you’re using the [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) (and you should be), then your wrapper jar will be in a directory off the project root directory called gradle.  This is one place where it might make sense to establish a convention for your project about where dependencies can be defined.  Let’s create a file containing definitions of our dependencies as per the example above:

```groovy
// gradle/libraries.gradle
ext {
    hibernateCoreVersion = "3.6.7.Final"
    guavaVersion = "23.0"
    junitVersion = "4.+"
 
    libs = [
        hibernate-core: "org.hibernate:hibernate-core:$hibernateCoreVersion",
        guava: "com.google.guava:guava:$guavaVersion",
        junit: "junit:junit:$junitVersion"
    ]
}
```

Now, to enable each sub-project / module to be able to reference these, you’ll need to apply the gradle file to each project.  This is done very simply by adding to the top level `build.gradle` file the following:

```groovy
// root project build.gradle
subprojects {
    apply from "$rootDir/gradle/libraries.gradle"
}
```

Now, within each module’s `build.gradle` (or `mymodule.gradle` if your module is called `mymodule` and you renamed your module build files for clarity as I showed you [here](/gradle-for-large-projects-customising-multi-project-build-files)), you can do the following:

```groovy
// module's build.gradle
dependencies {
    implementation libs.hibernate.core
    api libs.guava
    testImplementation libs.junit
}
```

The result is that all your dependencies are defined in one location but each module is free to include only the modules that are needed by that module.

