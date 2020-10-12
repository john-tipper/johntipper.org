---
title: Gradle – Integration Testing for Gradle Plugin Development
author: John Tipper
date: 2018-07-18
hero: ./images/IMG_1398.jpeg
excerpt: How to get started with Integration Testing for Gradle plugin development.
---

When building a Gradle plugin, some tests only work when you run them in the context of a Gradle build, i.e. you need Gradle to be running to get access to the full Gradle context. Starting such a process (by GradleRunner) is slow and it may not be possible to do all your testing via ProjectBuilder or plain unit tests.  As such, you may wish to separate out your unit tests from the longer running tests.  You can do this via integration or functional tests.

You can create a new suite of tests (which we will call functional tests for the purpose of this blog post) by editing your `build.gradle`​ file. The snippet below is for running the tests using Spock (i.e. the tests are written in Groovy).

```java
// build.gradle
 
// define the source used for your functional tests
sourceSets {
    functionalTest {
        groovy {
            srcDir file('src/test/groovy')
        }
        resources {
            srcDir file('src/test/resources')
        }
        compileClasspath += sourceSets.main.output + configurations.testRuntime
        runtimeClasspath += output + compileClasspath
    }
}


// define a test task that will execute these tests
task functionalTest(type: Test) {
    testClassesDirs = sourceSets.functionalTest.output.classesDirs
    classpath = sourceSets.functionalTest.runtimeClasspath
}
 
// let's make functionalTest a dependency on the check task
check.dependsOn functionalTest
```
If we were building a Gradle plugin using the java-gradle-plugin development plugin, then you can make the functional tests have the `gradleTestKit()` dependency injected by the plugin by configuring the `gradlePlugin`​ closure:

```java
gradlePlugin {
    testSourceSets sourceSets.functionalTest
    plugins {
        // as per normal
    }
}
```

Further details can be found in the [Gradle docs](https://docs.gradle.org/current/userguide/test_kit.html#sub:test-kit-automatic-classpath-injection).