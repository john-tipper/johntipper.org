---
title: Gradle Plugin Development – Functional Testing with Spock
author: John Tipper
date: 2018-07-18
hero: ./images/Jan 05 006.jpeg
excerpt: Introduction to functional testing with Spock.
---

[Spock](http://spockframework.org/) is a beautifully expressive testing framework. Personally, I find it results in perhaps a 1/3 less code than JUnit and the tests are to my eye much easier to understand.

Here’s an example of a functional test (see the [earlier blog post](/gradle-plugin-development-functional-testing-with-spock) for details) of how to use Spock and [GradleRunner](https://docs.gradle.org/current/userguide/test_kit.html#sec:functional_testing_with_the_gradle_runner) to perform functional tests.

```groovy
class MyPluginFunctionalSpec extends Specification {
    @Rule
    final TemporaryFolder testProjectDir = new TemporaryFolder()
 
    File buildFile
    File settingsFile
 
    def setup() {
        buildFile = testProjectDir.newFile('build.gradle')
        buildFile << """
            // put your default build.gradle for your tests here    
        """
 
        settingsFile = testProjectDir.newFile('settings.gradle')
        settingsFile << """
            rootProject.name = "myplugin"
        """
    }
 
    def "my-task-added-by-my-plugin runs without failing"() {
        given:
        buildFile << """
            // I want to do something different
            plugins {
                id 'org.tipper.myplugin'
            }  
            // etc  
        """
 
        when:
        def result = GradleRunner.create()
                     .withProjectDir(testProjectDir.root)
                     .withArguments('my-task-added-by-my-plugin')
                     .withPluginClasspath()
                     .build()
 
        then:
        result.task(":my-task-added-by-my-plugin").outcome == SUCCESS
    }
}
```

Further details can be found in the [Gradle docs](https://docs.gradle.org/current/userguide/test_kit.html#sub:test-kit-automatic-classpath-injection).