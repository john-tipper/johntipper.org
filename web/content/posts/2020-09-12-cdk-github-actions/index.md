---
title: Integrating AWS CDK into GitHub Actions
author: John Tipper
date: 2020-09-12
hero: ./images/2006_0408Image30063.jpeg
excerpt: Integrating AWS CDK into GitHub Actions
---

# Integrating AWS CDK into GitHub Actions

We want to be able to have changes to our infrastructure-as-code be deployed automatically. Specifically, we want changes to our repository to result in the following:

1. Build and test code our code.
2. Build our website.
3. Deploy our website infrastructure, including updating our website content.

## Choice of GitHub Actions as CI/CD

Now, one might be forgiven for thinking that if we're going to be deploying code to AWS that we might make use of AWS features, such as CodePipeline and CodeBuild.  However, GitHub Actions are completely free, whereas the AWS services are not.  Let's go with the free option...

## AWS Credentials

Create an IAM user with programmatic to AWS and store the access key and secret access key as secrets in the GitHub repository. The permissions associated with the user will be determined by the resources you define within your CDK stack.  Let's assume these secrets are called `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`.  GitHub Actions will make these secrets available to the actions and we don't need to store them in source code anywhere. We'll also assume that there is another secret called `AWS_REGION`, but clearly neither this nor `AWS_ACCESS_KEY_ID` actually need to be secret - we'll just store them in GitHub as secrets to completely decouple our AWS deployment details from the source code.

## GitHub Actions Workflow

At its simplest, a GitHub Action comprises a single workflow, which is a sequence of steps to carry out.  You can refer to other actions as part of these steps, where those actions may be defined by you or others.  For our purposes, we'll just define a single workflow.

### Workflow

Our workflow definition file is inside `.github/workflows/build.yml` (you're free to choose whatever workflow name you wish). We define the name of the workflow and when we want it to be triggered (on every push to the repository). We want it to run on a Ubuntu runner and perform a series of steps when it is triggered.

```yaml
name: Build

on: [push]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
     ... steps go here
```

Our first steps involve checking out the code from the repository and then configuring Node.js.  I need to install the AWS CDK and Gatsby - you may not need Gatsby if you're building your website some other way.  We define some caching of build dependencies to reduce build times for subsequent runs.

```yaml
      - uses: actions/checkout@v2

      - name: Use Node.js
        uses: actions/setup-node@v1
        with:
          node-version: '14.x'

      - name: Cache Node.js modules
        uses: actions/cache@v2
        with:
          path: ~/.npm
          key: ${{ runner.OS }}-node-${{ hashFiles('**/package-lock.json') }}
          restore-keys: |
            ${{ runner.OS }}-node-
            ${{ runner.OS }}-

      - name: Install CDK and Gatsby
        run: |
          npm install -g aws-cdk@1.62.0 gatsby-cli
```

We then configure Java and Gradle with caching of Gradle dependencies:

```yaml
      - name: Set up JDK 1.11
        uses: actions/setup-java@v1
        with:
          java-version: 1.11

      - name: Cache Gradle packages
        uses: actions/cache@v2
        with:
          path: ~/.gradle/caches
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle') }}
          restore-keys: ${{ runner.os }}-gradle

```

Now we're ready to define some steps which will build our project's CDK and generate the website. Clearly, you can put in whatever unit tests you wish for your application code: remember, CDK is just developing in your normal coding language.

```yaml
      - name: Build with Gradle
        run: ./gradlew cdkPrepare

      - name: Build Gatsby
        run: |
          pushd web
          npm ci
          gatsby clean
          gatsby build
          popd
```

Next step is to synthesize the CDK stack, which, as you'll recall, requires AWS credentials to perform a Route53 HostedZone lookup.

```yaml
      - name: Synth CDK
        run: |
          cdk synth \
          --app 'java -jar ./infrastructure/build/cdk/infrastructure-all.jar -apiLambdaPath ./infrastructure/build/cdk/api-lambdas.zip -webAssets ./infrastructure/build/cdk/web -domainName johntipper.org -region ${{ secrets.AWS_REGION }} -targetAccount ${{ secrets.AWS_TARGET_ACCOUNT }}' \
          --profile blog \
          --output build/cdk.out
        env:
          AWS_REGION: ${{ secrets.AWS_REGION }}
          AWS_ACCESS_KEY_ID: ${{ secrets.AWS_ACCESS_KEY_ID }}
          AWS_SECRET_ACCESS_KEY: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          AWS_TARGET_ACCOUNT: ${{ secrets.AWS_TARGET_ACCOUNT }}

```
 
### Status

In our repository [README.md](https://github.com/john-tipper/johntipper.org/blob/master/README.md), we'll define an image, which GitHub will update whenever a build occurs.  That image will (hopefully) show a green badge showing that our build works. We can refer to that image using this syntax:

```shell script
![Build status](https://github.com/<org>/<repo>/workflows/Build/badge.svg "GitHub Actions Build Status")
```

where `Build` is the name of the workflow that we define in our workflow file.