---
title: Introduction to the Cloud Resume Challenge
author: John Tipper
date: 2020-08-27
hero: ./images/NZ 046.jpeg
excerpt: Introduction to the Cloud Resume Challenge using AWS CDK & Java.
---

# Cloud Resume Challenge

Some months ago, a very generous engineer came up with the idea of the Cloud Resume Challenge.  The idea behind the challenge was to help inexperienced developers learn about developing in and for the cloud and to assist them with getting their first job.  That engineer was Forrest Brazeal and his blog post on the challenge is [here](https://forrestbrazeal.com/2020/04/23/the-cloud-resume-challenge/).  In short, Forrest generously offered to share people's CVs within his network if they undertook the challenge, which involved the creation of a website to host a CV and obtaining the AWS Cloud Practitioner certification in the process.

The challenge has come to an end, but its requirements remain as really interesting exercises in learning how to achieve certain things in the cloud domain.  The whole point of the challenge was not to give too much away so that readers would be forced to research, to study and to learn by doing, so this blog post is specifically not going to act as a spoiler for anyone who might still be working their way through the challenge's exercises.  Rather, I want to take the reader through some different approaches to achieving some of the goals of the challenge.

Let's put the requirements of the challenge into very general terms:

1. A static website, hosted in S3, accessed via TLS.
2. A backend API, callable by the website.
3. Automated CI/CD, such that when changes are pushed to the source-code repository hosting the user's code, the changes are automatically deployed to the website and/or backend API.

## Technology choices - Java, CDK and OpenAPI

The challenge asked for some specific implementation details, such as writing the backend in Python and defining the infrastructure using the [AWS Serverless Application Model](https://aws.amazon.com/serverless/sam/).  In order to respect the challenge and not give too much away, we'll do something different here in terms of our technology choices.

There are a number of reasons why it might make sense to write your backend in Python: for a start, it's an interpreted language, meaning there is not a need to compile the function into an executable first. Small Lambda functions can be directly entered into the AWS console, if you're that way inclined.  Finally, the source code size is small.

If you're an experienced engineer, you'll care as much about how someone is going to maintain your code as you do about writing it in the first place, hopefully even more so.  Incidentally, this should also include how someone is going to decommission your pride and joy.  If you're writing your code just for you, then you're free to make any technology choices you wish.  However, whilst I can code in Python and Typescript, my work colleagues cannot, which means that code written in these languages is not able to be maintained by all of my colleagues.  If you're ever been an engineering manager, you'll hate key man dependencies.

As my place of work is a Java-shop, I took the decision to write all our backend code in Java.  Everyone is able to contribute.  Here are the pros and cons of that decision, as I see them:

1. Everyone can read the code and contribute.
2. The Java ecosystem is extremely mature, meaning that there are a lot of tooling, testing and general libraries available. 
3. Java is fast, so large functions will execute quickly. 
4. The JVM is relatively slow to start compared with other languages, so very small functions will be slower than other language choices.

Our serverless backend has an extremely light load, so the speed arguments are moot. Points (1) and (2) count for a lot.

Infrastructure as code (IaaC) is a concept that says that it is possible to define your infrastructure using some form of declarative mechanism.  Declarative here means stating your desired end-state: I'd like 2 servers, please, irrespective of whether there are none now or we already have 10. Examples of IaaC are Terraform, CloudFormation, or the AWS SAM (which is like a superset of CloudFormation).  All of these have the drawback, however, of being written in varying configuration languages, such as YAML.  This makes the resulting files very verbose, hard to follow and extremely difficult to test. Enter AWS CDK.

### AWS Cloud Development Kit
 
The AWS Cloud Development Kit (CDK) has a simple proposition that it offers: developers should be free to code their infrastructure in a high level language of their choice.  Practically, what this means is that infrastructure code is written in a high level language and it generates (or synthesizes, in CDK parlance) the declarative definition of the infrastructure in CloudFormation.  A subsequent step involves taking this definition and deploying it to AWS.

The glue that sits between the high-level language and the act of synthesis is [JSII](https://github.com/aws/jsii); it's a library which allows interaction between a native language and Javascript.  The runtime architecture of JSII is in the [docs](https://github.com/aws/jsii/blob/master/docs/runtime-architecture.md) but simply put, it involves native code interacting with Javascript and the Javascript libraries then performing the synthesis into CloudFormation.  Developers can also write additional libraries in Typescript or Javascript and then compile and distribute these libraries for other languages like Java, Python or C#.

Typescript/Javascript is at the core of CDK: if you write in one of these languages then your libraries can be used by any other language for which there is a JSII binding.  However, for my place of work, it would be introducing another technology and one which would exclude most of my colleagues from contributing.  For this reason, our CDK code is written in Java.  We're not going to share our proprietary code with the wider world anyway, so the only code that it needs to be compatible with is our own, so we're not losing much here.  Note that this would probably be a very bad choice if we ever had aspirations of wanting to share our code with anyone else.

However, to keep things simple and to avoid introducing complexity in the form of new languages, the CDK that I'm going to show you is going to be coded in Java, too.  On the plus side, this means that we just have one toolchain and build process for all our infrastructure and backend services.

### OpenAPI

We know we want to build an API for our backend services.  If we wish to describe this API to other developers, or clients who may wish to call it, then we need a means of describing it: the endpoints we can call, the parameters we should pass and the format of the data we can expect back.  This description should be an interface into our API and it shouldn't be necessary to read through our codebase to find it out.  We've got a couple of options here.

We could use a technology such as GraphQL or protobuf with gRPC, but in the world of financial services, these are still relatively new technologies and not yet widely adopted.  REST APIs are common, so we're going to use that here - proposing a REST API shouldn't be controversial.  [OpenAPI](https://swagger.io/specification/) (formerly known as Swagger) is a way of codifying an API and there are lots of tools that can be used to convert an OpenAPI definition into code, which means there's less code for us to write and it avoids the possibility of making mistakes in implementing our API.  We can also import an OpenAPI spec into CDK directly: changing the spec will also result in having our infrastructure updated immediately.

### Website

How you choose to build a website is entirely up to you - you could literally just hard-code a HTML file. I am going to show you how I deploy this blog, which is written using [Gatsby](https://www.gatsbyjs.com), but you're free to go your own way.  I'm going to assume that somewhere there is an output directory containing all the files that need to be uploaded to the cloud. 

## What we'll build

We've discussed briefly some technologies that can be used to define and create a website with backend services.  The next series of blog posts will take you through how I used these to deploy this blog.  If you're working through the Cloud Resume Challenge then hopefully these posts will give you some ideas for how to make progress, without me giving the game away.  Feel free to reach out via Twitter [@john_tipper](https://twitter.com/john_tipper) if you've got any questions, queries or comments. 