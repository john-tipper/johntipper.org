---
title: Setting up the AWS CDK with Java & Gradle
author: John Tipper
date: 2020-08-29
hero: ./images/Tarin Khowt.jpeg
excerpt: How the AWS CDK can be used with Java, with an introduction to Gradle en-route.
---

# AWS CDK with Java and Gradle

Using the CDK with Java is not necessarily a natural choice for most projects - if you're starting from scratch then I'd strongly suggest you use Typescript, but the [previous post](/introduction-to-the-cloud-resume-challenge) described why much of my CDK work is in Java (it's around enabling my colleagues to contribute and maintain our codebase).  This post is about how to set up a basic AWS CDK project with Java. We're going to cover the Gradle build tool too, as the very essence of building for the cloud is in automation, and the first part of automation is being able to run every part of your build by script.

## Pre-requisites

It's assumed that you have the following already installed on your computer:

- JRE or JDK, version 1.8 or better.
- a Gradle distribution: you need to be able to run `gradle` command line tool.

## Project structure 

We're going to use [Gradle](https://gradle.org) as our build tool - in my opinion, it's a lot more user-friendly than Maven, plus it's got a rich ecosystem of plugins that can add functionality easily.  We're going to create a multi-module Gradle project, where the first module is our CDK infrastructure.

```shell script
> mkdir blog
> cd blog
> gradle init 
```

The `init` command will create a basic Gradle project which we can then modify. 

```shell script
├── build.gradle  
├── gradle
│   └── wrapper
│       ├── gradle-wrapper.jar  
│       └── gradle-wrapper.properties  
├── gradlew  
├── gradlew.bat  
└── settings.gradle  
```

### Gradle Wrapper

The [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) is a script which can be used to invoke a defined version of Gradle. If necessary, that version will be downloaded.  It's a neat way of ensuring that your build is repeatable and will always work on your colleagues' computers, irrespective of which version of Gradle they have installed.  The Gradle wrapper is the `gradlew` script in the root directory (or `gradlew.bat` if you're developing on Windows). 

First, let's edit our `build.gradle` file to define which version of Gradle we will use:

```java
// build.gradle
wrapper {
    gradleVersion = "6.3"
    distributionType = Wrapper.DistributionType.ALL
}
```

Now run the wrapper task to install the version of Gradle we've just defined (the version that was previously there will defined by whichever version of Gradle you have installed on your computer).

```shell script
> ./gradlew wrapper
> ./gradlew --version
./gradlew --version

------------------------------------------------------------
Gradle 6.3
------------------------------------------------------------

Build time:   2020-03-24 19:52:07 UTC
Revision:     bacd40b727b0130eeac8855ae3f9fd9a0b207c60

Kotlin:       1.3.70
Groovy:       2.5.10
Ant:          Apache Ant(TM) version 1.10.7 compiled on September 1 2019
JVM:          11.0.7 (Amazon.com Inc. 11.0.7+10-LTS)
OS:           Mac OS X 10.15.3 x86_64
```

Let's also set up our project to pull in dependencies as required from maven Central. Here's our `build.gradle` file in full:

```java
plugins {
  id 'java-library'
}

group 'org.johntipper'
version = "0.1.0-SNAPSHOT"

wrapper {
    gradleVersion = "6.3"
    distributionType = Wrapper.DistributionType.ALL
}

allprojects {
    repositories {
        mavenCentral()
    }

    group = rootProject.group
    version = rootProject.version
}
```

We define the group and version of the project and all sub-modules that we will define in the future. 

### Gradle sub-modules

The top level directory is just a container for sub-modules, we're not going to build anything there. All our code will be in one of several sub-modules we will create; these are described in detail in the [Gradle documentation](https://guides.gradle.org/creating-multi-project-builds/).

 ```java
// settings.gradle
rootProject.name = 'blog'

include "infrastructure"
 ```

Now create a sub-module called `infrastructure` where we will define our CDK infrastructure:

```shell script
> mkdir infrastructure
```

We need to create another file called `build.gradle` within that newly-created directory where we will define how we will build that sub-module. 

```shell script
├── ...  
├── infrastructure
│   └── build.gradle
│   └── src
│       └── main
│           └── java
└── ...  
```

Here is the `build.gradle` file for our infrastructure project in full:

```java
// infrastructure/build.gradle
plugins {
    id 'java-library'
    id 'application'
    id 'com.github.johnrengelman.shadow' version '5.2.0'

}

def CDK_VERSION = "1.60.0"

dependencies {
    implementation "software.amazon.awscdk:core:${CDK_VERSION}"
    implementation "software.amazon.awscdk:route53:${CDK_VERSION}"
    implementation "software.amazon.awscdk:route53-targets:${CDK_VERSION}"
    implementation "software.amazon.awscdk:route53-patterns:${CDK_VERSION}"
    implementation "software.amazon.awscdk:ses:${CDK_VERSION}"
    implementation "software.amazon.awscdk:certificatemanager:${CDK_VERSION}"
    implementation "software.amazon.awscdk:s3:${CDK_VERSION}"
    implementation "software.amazon.awscdk:s3-deployment:${CDK_VERSION}"
    implementation "software.amazon.awscdk:cloudfront:${CDK_VERSION}"
    implementation "software.amazon.awscdk:apigateway:${CDK_VERSION}"
    implementation "software.amazon.awscdk:dynamodb:${CDK_VERSION}"
    implementation "software.amazon.awscdk:events:${CDK_VERSION}"
    implementation "software.amazon.awscdk:events-targets:${CDK_VERSION}"
    implementation "software.amazon.awscdk:lambda:${CDK_VERSION}"
    implementation "software.amazon.awscdk:lambda-event-sources:${CDK_VERSION}"

    implementation 'commons-cli:commons-cli:1.4'
    implementation 'org.slf4j:slf4j-log4j12:1.7.28'
    implementation 'com.github.spullara.mustache.java:compiler:0.9.6'
    implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.10.4'

    testCompile group: 'junit', name: 'junit', version: '4.12'
}

application {
    mainClassName = 'org.johntipper.blog.aws.cdk.webapp.WebBackendApp'
}

```

From the top, we are declaring this project to be a Java one via the [java-library plugin](https://docs.gradle.org/current/userguide/java_library_plugin.html), we wish to use the [application plugin](https://docs.gradle.org/current/userguide/application_plugin.html) which will build an executable jar for us and we also use th [shadow plugin](https://imperceptiblethoughts.com/shadow/) which will ensure that our executable jar has all necessary dependencies bundled up into it.

We then define a whole bunch of CDK dependencies - you don't need all of them now for this particular blog post, but you will do by the end.  We define a couple of additional dependencies, whose purpose will become apparent shortly, plus include JUnit for unit-testing.

We define the class of our application that will be called when we execute our jar. Let's create a do-nothing main class:

```java
package org.johntipper.blog.aws.cdk.webapp;

public class WebBackendApp {
    public static void main(String[] args) {
        System.out.println("Hello world!");
    }
}
```

then build and execute it:

```shell script
>./gradlew shadowJar
> ./infrastructure/build/libs/infrastructure-0.1.0-SNAPSHOT-all.jar
Hello World!
```

We now have a working toolchain, so let's introduce the CDK.

## CDK Setup

### Installation

Detailed installation instructions for the CDK are [here](https://docs.aws.amazon.com/cdk/latest/guide/getting_started.html).

```shell script
>npm install -g aws-cdk
>cdk --version
```

### AWS Credentials with multiple AWS accounts

**UPDATE:** ignore this section, as things have come a long way with cross-account deployments using CDK.  Use [cdk-assume-role-credential-plugin](https://github.com/aws-samples/cdk-assume-role-credential-plugin), details [here](https://aws.amazon.com/blogs/devops/cdk-credential-plugin/), and see a later blog post by me for setting it up with CDK Pipelines.  

We are going to interact with the CDK command line tool in two ways: firstly, to synthesize a CloudFormation template that describes the infrastructure that we wish to create and secondly to initiate the deployment.  Those tasks are accomplished by means of the `cdk synth` and `cdk deploy` commands.

The `deploy` command obviously requires AWS credentials to work.  Depending on your infrastructure, you *may* need AWS credentials for the `synth` command to work too: this will be the case if your code performs a lookup inside AWS, e.g. it determines which VPCs exist to deploy a resource into.

You should never deploy with your AWS root credentials - always create a user or role which has whatever permissions you require (and no more) and use AWS credentials for that user.  If you lose control of tightly-scoped credentials it's less of a security disaster than if you lose control of your account root credentials: someone malicious can run up a very large bill in your name in a short period of time otherwise.

If your credentials are in an AWS credentials file (e.g. `~/.aws/credentials`) then you may have several roles that you wish to switch between. Each role will have a profile in the credentials file, where each profile will have separate credentials.  You can tell CDK to use a particular profile's credentials by means of the `--profile <profile name>` command line argument, if you don't wish to use the default one. 

It may be the case that you have several AWS accounts: [AWS Organizations](https://aws.amazon.com/organizations/) is a nice way of defining many accounts, all tied back to one root account.  You can then deploy resources to one account without having to worry about wht else might be running in that account. Equally, you may wish to deploy to multiple AWS accounts **at the same time**, in which case CDK will need credentials for different accounts. You can't specify a single AWS profile for CDK to use, as that means CDK will only have one set of credentials.

To solve this problem, we use a plugin mechanism for CDK. CDK will call our plugin when it runs and our plugin will be responsible for telling CDK which profile to use for any particular target account.

If you do not have multiple AWS accounts or do not wish to deploy to multiple accounts simultaneously, then skip to the next section.

Create the following plugin file anywhere on your computer and modify the logic as required to return the name of an appropriate profile:

```javascript
// cdk-profile-plugin.js
const { CredentialProviderChain } = require('aws-sdk');
const AWS = require('aws-sdk');

let getEnv = function(accountId) {
    // TODO: insert logic to get your desired profile name
    console.log("getEnv called with acc id: " + accountId);
    let profileName = '';
    if (accountId == "111111111111") {
        profileName "my-profile-for-account-1";
    }
    // further logic here

    return profileName;
};

let getProvider = async (accountId, mode) => {

    console.log("getProvider called with acc id: " + accountId);

    let profile = getEnv(accountId);
    let chain = new CredentialProviderChain([
        new AWS.SharedIniFileCredentials({
            profile: profile,
        }),
    ]);
    let credentials = await chain.resolvePromise();
    return Promise.resolve({
        accessKeyId: credentials.accessKeyId,
        secretAccessKey: credentials.secretAccessKey,
        sessionToken: credentials.sessionToken,
    });
};

module.exports = {
    version: '1',
    init: host => {
        console.log('Init loading cdk profile plugin', host);
        host.registerCredentialProviderSource({
            name: 'cdk-profile-plugin',
            canProvideCredentials(accountId) {
                canProvide = true; // TODO: your logic to determine whether should use the code in this file or not (optional)
                return Promise.resolve(canProvide);
            },
            getProvider,
            isAvailable() {
                return Promise.resolve(true);
            },
        });
    },
};
```

When you execute CDK, call it with the following flag:

```javascript
--plugin /path/to/cdk-profile-plugin.js
```

CDK will then call the plugin for each account being targeted for deployment and the plugin will return the name of the profile to use. Provided that your credentials file has credentials for that profile, CDK will then use those credentials for that profile to interact with AWS for those particular resources.

I can't claim credit for this mechanism and I can't remember where I first saw this as a solution, but the code above is heavily taken from Thomas de Ruiter's [Binx blog here](https://binx.io/blog/2020/01/30/building-an-aws-cdk-credential-provider/). It's a pretty neat solution, I think.

### CDK synthesis

The `cdk synth` command will execute our code and output a CloudFormation template into a directory (strictly speaking, it's called a CloudAssembly).  There are some parameters that are important that we need to pass it.  We will call CDK like this:

```shell script
> cdk synth --output cdk-system/build/cdk.out \
      --app 'java -jar /path/to/our/fat-jar.jar <params>'
```

The `--output <path>` parameter determines where our CloudAssembly code will be written.

The `--app <command>` is the command CDK needs to execute in order to synthesize our CloudASsembly definition.

The main class defines a variable of type [`software.amazon.awscdk.core.App`](https://docs.aws.amazon.com/cdk/api/latest/java/index.html), which has a method called `synth()`, which is supposed to output our code.  However, the assembly process requires JSII and so we're forced to call `cdk` to call our app, rather than being able to execute our app directly. Frustrating, not-intuitive and it's where the notion of coding in your own loanguage starts to break down.

If we want to pass parameters to our synthesis, then we have two options: we can either pass via `--c var=val` parameters to the `cdk` command, or we can pass parameters to our app directly.  Personally, I find it easier to unit-test and provide better error messages to users if the parameters aren't correct by the second method (I found the CDK client a bit wanting in this regard), so that's what we're going to do here.  This is why our dependencies included [Apache Commons CLI](https://commons.apache.org/proper/commons-cli/), as we're going to treat input parameters as just another command line argument passed to a standard Java application.

### CDK do-nothing

Let's create a basic do-nothing app that is integrated with CDK.

First, let's create a class which will hold some config that we want to pass to our app:

```java
public class WebBackendStackConfig {
    public static final String API_LAMBDA_PATH_KEY = "apiLambdaPath";
    public static final String TARGET_ACCOUNT_KEY = "targetAccount";
    public static final String REGION_KEY = "region";
    public static final String DOMAIN_NAME_KEY = "domainName";

    private final String domainName;

    private final String apiLambdaPath;

    private final String targetAccount;

    private final String region;


    public WebBackendStackConfig(String domainName, String apiLambdaPath, String targetAccount, String region) {
        this.domainName = domainName;
        this.apiLambdaPath = apiLambdaPath;
        this.targetAccount = targetAccount;
        this.region = region;
    }

    public String getDomainName() {
        return domainName;
    }

    public String getApiLambdaPath() {
        return apiLambdaPath;
    }

    public String getTargetAccount() {
        return targetAccount;
    }

    public String getRegion() {
        return region;
    }


    public static WebBackendStackConfig fromCommandLine(CommandLine cmd) {
        return new WebBackendStackConfig(cmd.getOptionValue(DOMAIN_NAME_KEY), cmd.getOptionValue(API_LAMBDA_PATH_KEY), cmd.getOptionValue(TARGET_ACCOUNT_KEY),cmd.getOptionValue(REGION_KEY));
    }
}
```

Now let's define our main app:

```java
public class WebBackendApp {

    private static final Logger LOG = LoggerFactory.getLogger(WebBackendApp.class);

    final App app;

    public WebBackendApp(CommandLine cmd) throws IOException {

        this.app = new App();

        WebBackendStackConfig webStackConfig = WebBackendStackConfig.fromCommandLine(cmd);

        WebBackendStack webBackendStack = new WebBackendStack(
                                              app,
                                              "WebBackendStack",
                                              StackProps.builder()
                                                        .env(Environment.builder()
                                                                        .account(webStackConfig.getTargetAccount())
                                                                        .region(webStackConfig.getRegion())
                                                                        .build())
                                                        .stackName("WebBackendStack")
                                                        .tags(Map.of("cdk", Boolean.toString(true)))
                                                        .build(),
                                              webStackConfig);

    }

    public void synth() {

        app.synth();
    }

    public static void main(String[] args) {
        Options options = new Options();

        options.addOption(Option.builder(REGION_KEY)
                                .argName(REGION_KEY)
                                .desc("AWS region of the deployment.")
                                .hasArg()
                                .required(true)
                                .build());

        options.addOption(Option.builder(TARGET_ACCOUNT_KEY)
                                .argName(TARGET_ACCOUNT_KEY)
                                .desc("AWS target account.")
                                .hasArg()
                                .required(true)
                                .build());

        options.addOption(Option.builder(API_LAMBDA_PATH_KEY)
                                .argName(API_LAMBDA_PATH_KEY)
                                .desc("Path to the cognito lambda bundle.")
                                .hasArg()
                                .required(true)
                                .build());

        options.addOption(Option.builder(DOMAIN_NAME_KEY)
                                .argName(DOMAIN_NAME_KEY)
                                .desc("Domain name of the website.")
                                .hasArg()
                                .required(true)
                                .build());

        CommandLineParser parser = new DefaultParser();

        try {

            CommandLine cmd = parser.parse(options, args);

            WebBackendApp cdkApp = new WebBackendApp(cmd);

            cdkApp.synth();

        } catch (MissingArgumentException | MissingOptionException | UnrecognizedOptionException e) {
            System.err.println(e.getMessage());
            HelpFormatter formatter = new HelpFormatter();

            formatter.printHelp(120, "java -cp /path/to/jar org.johntipper.blog.aws.cdk.webapp.WebBackendApp ARGS", "Args:", options, "", false);

            System.exit(1);

        } catch (Exception e) {

            LOG.error("Error when attempting to synthesize CDK: {}", e.getMessage());

            e.printStackTrace();

            System.exit(-1);
        }
    }
}
```

Basically, we define a CDK stack which can be configured with a target account and target AWS region and the act of running our `main()` means we parse input parameters and error if they are incorrect, otherwise we call for the `synth()` of our stack. Ignore the `apiLambdaPath` and `domainName` parameters for the moment, we'll explain those later.

Finally, let's create our do-nothing CDK stack, which creates no resources:

```java
public class WebBackendStack extends Stack {
    public WebBackendStack(Construct scope, String id, StackProps props, WebBackendStackConfig stackConfig) throws IOException {
        super(scope, id, props);
    }

}
```

We can test this:

```shell script
> ./gradlew shadowJar
> cdk synth --output infrastructure/build/cdk.out \
      --app 'java -jar infrastructure/libs/infrastructure-0.1.0-SNAPSHOT-all.jar -apiLambdaPath /any/path/here -targetAccount <your AWS account number> -region <AWS target region> -domainName example.com'
```

## Summary

All non-trivial projects have a certain amount of scaffolding that is required to get them up and running, but we've successfully built an AWS CDK application that uses Java, is built by Gradle (so is capable of automation) and which can be integrated into an arbitrarily complex AWS environment involving simultaneous deployment to multiple AWS accounts.  The next post will be about how we define the infrastructure to support a static website by means of CDK. 
