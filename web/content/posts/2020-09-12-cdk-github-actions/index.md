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

