---
title: A static website with API backend using AWS CDK and Java
author: John Tipper
date: 2020-09-12
hero: ./images/2006_0407Image0321.jpeg
excerpt: An example of creating a static website using AWS CDK and Java
---

# Creating a static website with AWS CDK and Java

Let's take a very quick canter through creating a minimal static website with a REST API backend using AWS CDK. We'll host the website in S3 (meaning it's cheap), but we'll put a CloudFront CDN distribution in front of it (meaning that your S3 data costs can be minimised and you can offer a low latency read experience for your readers, wherever in the world they may be). We'll also add a TLS certificate so that visitors can have confidence in your site.

## TLS

Let's start with the plumbing - we assume that you've got a domain name that you've registered somewhere.  This doesn't have to be AWS. We'll assume that you have created a Route53 Hosted Zone for your domain in the AWS console separately: there's a guide [here](https://docs.aws.amazon.com/Route53/latest/DeveloperGuide/CreatingHostedZone.html) if you're unsure of how to do this.

```java
// Route53 hosted zone created out-of-band
IHostedZone hostedZone = HostedZone.fromLookup(this, "HostedZone", HostedZoneProviderProps.builder()
                                                                                          .domainName(stackConfig.getDomainName())
                                                                                          .build());

DnsValidatedCertificate websiteCertificate = DnsValidatedCertificate.Builder.create(this, "WebsiteCertificate")
                                                                            .hostedZone(hostedZone)
                                                                            .region("us-east-1")
                                                                            .domainName(stackConfig.getDomainName())
                                                                            .subjectAlternativeNames(List.of(String.format("www.%s", stackConfig.getDomainName())))
                                                                            .build();
```

Because we are creating the Hosted Zone out of band (i.e. not as part of the tutorial) and we wish to refer to it within our CDK construct, we need to do a lookup of it.  This particular call will interrogate AWS during CDK synthesis, meaning AWS credentials will be required from this step.  We then create a TLS certificate from AWS Certificate Manager, validated by DNS, in just 6 lines of code! Note that we create the certificate in `us-east-1` because we want to use it with CloudFront, and we also define a Subject Alternative Name (SAN) for the certificate. This means that users can refer to our website with a `www.` prefix and their browser will still trust the certificate we serve up.

When CDK creates the certificate, it may take a little while to complete. This is because under the covers, AWS will create the certificate, create DNS entries in our Hosted Zone that prove to AWS that we control our domain, then wait for AWS to recognise those certificate entries and thus provide us with a validated certificate. 

## S3 static website with CloudFront CDN

Let's create an S3 bucket for use as a website container.  CDK makes this super easy to do and the API is really nice to work with.

```java
Bucket websiteBucket = Bucket.Builder.create(this, "WebsiteBucket")
                                     .bucketName(String.format("website-%s", props.getEnv().getAccount()))
                                     .encryption(BucketEncryption.UNENCRYPTED)
                                     .websiteIndexDocument("index.html")
                                     .removalPolicy(RemovalPolicy.DESTROY)
                                     .build();
```

Now let's think about adding a CloudFront CDN in front of the bucket, so that the website contents are cached geographically close to readers.
```java
OriginAccessIdentity webOai = OriginAccessIdentity.Builder.create(this, "WebOai")
                                                          .comment(String.format("OriginAccessIdentity for %s", stackConfig.getDomainName()))
                                                          .build();


websiteBucket.grantRead(webOai);

CloudFrontWebDistribution cloudFrontWebDistribution = CloudFrontWebDistribution.Builder.create(this, "CloudFrontWebDistribution")
                                                                                       .comment(String.format("CloudFront distribution for %s", stackConfig.getDomainName()))
                                                                                       .viewerCertificate(ViewerCertificate.fromAcmCertificate(websiteCertificate, ViewerCertificateOptions.builder()
                                                                                                                                                                                           .aliases(List.of(stackConfig.getDomainName()))
                                                                                                                                                                                           .build()))
                                                                                       .originConfigs(List.of(SourceConfiguration.builder()
                                                                                                                                 .behaviors(List.of(Behavior.builder()
                                                                                                                                                            .isDefaultBehavior(true)
                                                                                                                                                            .defaultTtl(Duration.minutes(5))
                                                                                                                                                            .maxTtl(Duration.minutes(5))
                                                                                                                                                            .build()))
                                                                                                                                 .s3OriginSource(S3OriginConfig.builder()
                                                                                                                                                               .originAccessIdentity(webOai)
                                                                                                                                                               .s3BucketSource(websiteBucket)
                                                                                                                                                               .build())
                                                                                                                                 .build()
                                                                                       ))
                                                                                       .priceClass(PriceClass.PRICE_CLASS_100)
                                                                                       .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                                                                                       .errorConfigurations(List.of(CfnDistribution.CustomErrorResponseProperty.builder()
                                                                                                                                                               .errorCode(403)
                                                                                                                                                               .responseCode(200)
                                                                                                                                                               .responsePagePath("/index.html")
                                                                                                                                                               .build(),
                                                                                                                    CfnDistribution.CustomErrorResponseProperty.builder()
                                                                                                                                                               .errorCode(404)
                                                                                                                                                               .responseCode(200)
                                                                                                                                                               .responsePagePath("/index.html")
                                                                                                                                                               .build()))
                                                                                       .build();
```
We start by defining an [Origin Access Identity](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html), which is basically a way of defining who or what may read from our bucket - it's a special CloudFront user.  It means that ClodFront can get our files from S3, but a user can't just access the S3 bucket directly. We need to grant permission to the OAI to access our bucket.  This "grant" pattern is pretty common in the CDK for defining who or what may access a resource, it's not just S3 resources: you'll see it for many other resources such as SQS, Parameter Store etc.

We use the TLS certificate we created earlier and we define that users should be redirected to HTTPS if they try to access using HTTP.  Note that the price class of the CDN defines where in the world you want your static data cached.  `PRICE_CLASS_100` is the cheapest caching option but you can choose others, see [here](https://docs.aws.amazon.com/cdk/api/latest/python/aws_cdk.aws_cloudfront/PriceClass.html) for details.

Now let's wrap this up by some housekeeping: we want the CDN to redirect HTTP to HTTPS and CloudFront requires a DNS entry at the apex level, so we create a DNS entry that points at our CDN distribution.
 
```java
HttpsRedirect webHttpsRedirect = HttpsRedirect.Builder.create(this, "WebHttpsRedirect")
                                                      .certificate(websiteCertificate)
                                                      .recordNames(List.of(String.format("www.%s", stackConfig.getDomainName())))
                                                      .targetDomain(stackConfig.getDomainName())
                                                      .zone(hostedZone)
                                                      .build();


ARecord apexARecord = ARecord.Builder.create(this, "ApexARecord")
                                     .recordName(stackConfig.getDomainName())
                                     .zone(hostedZone)
                                     .target(RecordTarget.fromAlias(new CloudFrontTarget(cloudFrontWebDistribution)))
                                     .build();

```

## Adding an API

So far, we've created infrastructure suitable for hosting a static website. Let's add support for a REST API.

To keep thongs neat, we'll start by creating a separate CDK Construct to hold all of our API infrastructure. We do this by creating a Java class that extends from `Construct`:

```java
public class HelloWorldApi extends Construct {

    private final IRestApi restApi;

    public HelloWorldApi(@NotNull Construct scope, @NotNull String id, StackProps props, WebBackendStackConfig stackConfig) throws IOException {
        super(scope, id);

    public HelloWorldApi(@NotNull Construct scope, @NotNull String id, StackProps props, WebBackendStackConfig stackConfig) throws IOException {
        super(scope, id);
        // we'll add more here in a moment...
 
   }

    public IRestApi getRestApi() {
        return restApi;
    }

```

You'll notice that we have a class member variable called `restApi` that refers to a CDK construct, with an associated getter. We will initiate that variable in the constructor.  This is a pattern by which we can expose CDK constructs to other parts of our stack, without needing to having one large monolith.  Despite the fact that those constructs will actually represent infrastructure, to the CDK they're just a Java variable.  This is the paradigm of CDK: just program normally.

Now we can define a lambda inside our `HelloWorldApi` construct.  Best practice is to not have a single lambda that does everything: you should split these out. We only have one function, so that makes things easy for us.

```java
SingletonFunction helloWorldLambda = SingletonFunction.Builder.create(this, "HelloWorldLambda")
                                                              .description("HelloWorld lambda to demonstrate integration with ApiGateway")
                                                              .code(Code.fromAsset(stackConfig.getApiLambdaPath()))
                                                              .handler(String.format("%s::handleRequest", "org.johntipper.blog.lambda.HelloWorldHandler"))
                                                              .timeout(Duration.seconds(10))
                                                              .runtime(Runtime.JAVA_11)
                                                              .memorySize(256)
                                                              .uuid(UUID.randomUUID()
                                                                        .toString())
                                                              .logRetention(RetentionDays.ONE_WEEK)
                                                              .build();

```

We need a unique identifier for the function and we don't define the name of the function, otherwise we'll get into trouble if we try to redeploy a different version.  The CDK API makes it simple to define how we want out function to behave with respect to timeouts, memory usage, log retention etc. Note that we define a path to where our executable code for the function should be found that we'll pass in.

Now that we have a function defined, we need to turn to defining permissions. We want to ensure that it may be executed by both ApiGateway as well as from the AWS console.

```java
// allow lambda to write logs, allow APIG & console to call the lambda
CfnPermission helloWorldRestPermission = CfnPermission.Builder.create(this, "HelloWorldRestPermission")
                                                              .action("lambda:InvokeFunction")
                                                              .principal("apigateway.amazonaws.com")
                                                              .sourceArn(String.format(
                                                                  "arn:aws:execute-api:%s:%s:*",
                                                                  props.getEnv()
                                                                       .getRegion(),
                                                                  props.getEnv()
                                                                       .getAccount()))
                                                              .functionName(helloWorldLambda.getFunctionName())
                                                              .build();

helloWorldLambda.grantInvoke(ServicePrincipal.Builder.create("apigateway.amazonaws.com")
                                                     .build());


Role apiGatewayRole = Role.Builder.create(this, "ApiGatewayRole")
                                  .assumedBy(ServicePrincipal.Builder.create("apigateway.amazonaws.com")
                                                                     .build())
                                  .roleName("ApiGatewayLambdaExecutionRole")
                                  .build();

apiGatewayRole.addToPolicy(PolicyStatement.Builder.create()
                                                  .resources(List.of("*"))
                                                  .actions(List.of("logs:*"))
                                                  .effect(Effect.ALLOW)
                                                  .build());

apiGatewayRole.addToPolicy(PolicyStatement.Builder.create()
                                                  .resources(List.of(helloWorldLambda.getFunctionArn()))
                                                  .actions(List.of("lambda:InvokeFunction"))
                                                  .effect(Effect.ALLOW)
                                                  .build());

```

Let's define our API using OpenAPI, where we define an endpoint hat the user will call and receive a hello world response (we define the api endpoints to reside behind an initial path prefix of `/api`).

```yaml
openapi: 3.0.0
info:
  title: Demo API
  description: REST API demonstrating how to integrate into a CloudFront distribution.
  version: 0.1.0

servers:
  - url: /api

x-amazon-apigateway-gateway-responses:
  DEFAULT_4XX:
    ResponseParameters:
      gatewayresponse.header.Access-Control-Allow-Origin: "'*'"
      gatewayresponse.header.Access-Control-Allow-Headers: "'*'"
      gatewayresponse.header.Access-Control-Allow-Methods: '''*'''
  UNAUTHORIZED:
    ResponseParameters:
      gatewayresponse.header.Access-Control-Allow-Origin: "'*'"
      gatewayresponse.header.Access-Control-Allow-Headers: "'*'"
      gatewayresponse.header.Access-Control-Allow-Methods: '''*'''
      StatusCode: '401'
  EXPIRED_TOKEN:
    ResponseParameters:
      gatewayresponse.header.Access-Control-Allow-Origin: "'*'"
      gatewayresponse.header.Access-Control-Allow-Headers: "'*'"
      gatewayresponse.header.Access-Control-Allow-Methods: '''*'''
      StatusCode: '401'

components:
  schemas:
    HelloWorldResponse:
      properties:
        message:
          type: string

paths:
  /hello:
    get:
      summary: Hello world endpoint.
      responses:
        '200':
          description: OK
          headers:
            Access-Control-Allow-Origin:
              schema:
                type: string
            Access-Control-Allow-Methods:
              schema:
                type: string
            Access-Control-Allow-Headers:
              schema:
                type: string
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/HelloWorldResponse'

      x-amazon-apigateway-integration:
        uri: "{{helloworld-lambda}}"
        passthroughBehavior: "when_no_match"
        httpMethod: "POST"
        type: "aws_proxy"

```

We can edit this file to our heart's content in an OpenAPI editor such as [Swagger Editor](https://editor.swagger.io). Note that there's a bit of a pain point here: we need to define via the `x-amazon-apigateway-integration` stanza what our Lambda execution endpoint will be, but we won't know this until the API is deployed. This forms a bit of a chicken and egg problem.  When you Google the problem, others solve this by deploying twice, or some manual post-deploy configuration.  We'll get around this by templating the value into the OpenAPI spec as part of the CDK synthesis. That's what the `{{helloworld-lambda}}` placeholder is, it's a Mustache template placeholder.  We template the API spec and inject our value which we get from the Lambda we created earlier, then pass that templated API to CDK to create an ApiGateway for us.

```java
// need to inject lambda execution ARN into OpenAPI spec, so we use mustache to template, then parse templated spec
Map<String, Object> variables = new HashMap<>();
variables.put("helloworld-lambda", String.format(
    "arn:aws:apigateway:%s:lambda:path/2015-03-31/functions/%s/invocations",
    props.getEnv()
         .getRegion(),
    helloWorldLambda.getFunctionArn()));

Writer writer = new StringWriter();
MustacheFactory mmf = new DefaultMustacheFactory();

Object openapiSpecAsObject;
try (Reader reader = new FileReader(new File("api.yaml"))) {
    Mustache mustache = mmf.compile(reader, "OAS");
    mustache.execute(writer, variables);
    writer.flush();

    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    openapiSpecAsObject = yamlMapper.readValue(writer.toString(), Object.class);

}

restApi = SpecRestApi.Builder.create(this, "OpenapiRestApi")
                             .restApiName("HelloWorld")
                             .apiDefinition(ApiDefinition.fromInline(openapiSpecAsObject))
                             .deploy(true)
                             .deployOptions(StageOptions.builder()
                                                        .stageName("api")
                                                        .build())
                             .build();
restApi.getNode()
       .addDependency(apiGatewayRole);

restApi.getNode()
       .addDependency(helloWorldRestPermission);

restApi.getDeploymentStage()
       .getNode()
       .addDependency(helloWorldRestPermission);

helloWorldLambda.addPermission(
    "AllowApiGatewayInvocation",
    Permission.builder()
              .action("lambda:InvokeFunction")
              .principal(ServicePrincipal.Builder.create("apigateway.amazonaws.com")
                                                 .build())
              .sourceArn(restApi.arnForExecuteApi())
              .build());
```

The beauty of the CDK is then that we can refer to this REST API defined within this construct, containing whatever arbitrary complexity we define via multiple Lambdas etc, by means of referring to simply the construct.  By this I mean we just need to add the construct into our stack:

```java
HelloWorldApi helloWorldApi = new HelloWorldApi(this, "HelloWorldApi", props, stackConfig);
```

Finally, if you access the API via a browser, you'll have Cross Origin Resource issues if you wish to call your API via a different domain name, for example `api.example.com`.  To get around this, we want to be able to hit our API via the same domain name as we use for our website, just via a different path. For example, reequest paths matching `example.com/api/*` should perhaps result in our API being called and all others just go through to our static website in S3. To achieve this, we can modify our CloudFront distribution to do this for us.

```java
CloudFrontWebDistribution cloudFrontWebDistribution = CloudFrontWebDistribution.Builder.create(this, "CloudFrontWebDistribution")
                                                                                       .comment(String.format("CloudFront distribution for %s", stackConfig.getDomainName()))
                                                                                       .viewerCertificate(ViewerCertificate.fromAcmCertificate(websiteCertificate, ViewerCertificateOptions.builder()
                                                                                                                                                                                           .aliases(List.of(stackConfig.getDomainName()))
                                                                                                                                                                                           .build()))
                                                                                       .originConfigs(List.of(SourceConfiguration.builder()
                                                                                                                                 .behaviors(List.of(Behavior.builder()
                                                                                                                                                            .isDefaultBehavior(true)
                                                                                                                                                            .defaultTtl(Duration.minutes(5))
                                                                                                                                                            .maxTtl(Duration.minutes(5))
                                                                                                                                                            .build()))
                                                                                                                                 .s3OriginSource(S3OriginConfig.builder()
                                                                                                                                                               .originAccessIdentity(webOai)
                                                                                                                                                               .s3BucketSource(websiteBucket)
                                                                                                                                                               .build())
                                                                                                                                 .build(),
                                                                                                              SourceConfiguration.builder()
                                                                                                                                 .behaviors(List.of(Behavior.builder()
                                                                                                                                                            .pathPattern("api/*")
                                                                                                                                                            .allowedMethods(CloudFrontAllowedMethods.ALL)
                                                                                                                                                            .build()))
                                                                                                                                 .customOriginSource(CustomOriginConfig.builder()
                                                                                                                                                                       .domainName(String.format("%s.execute-api.%s.amazonaws.com", helloWorldApi.getRestApi().getRestApiId(), stackConfig.getRegion()))
                                                                                                                                                                       .build())
                                                                                                                                 .build()
                                                                                       ))
                                                                                       .priceClass(PriceClass.PRICE_CLASS_100)
                                                                                       .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                                                                                       .errorConfigurations(List.of(CfnDistribution.CustomErrorResponseProperty.builder()
                                                                                                                                                               .errorCode(403)
                                                                                                                                                               .responseCode(200)
                                                                                                                                                               .responsePagePath("/index.html")
                                                                                                                                                               .build(),
                                                                                                                    CfnDistribution.CustomErrorResponseProperty.builder()
                                                                                                                                                               .errorCode(404)
                                                                                                                                                               .responseCode(200)
                                                                                                                                                               .responsePagePath("/index.html")
                                                                                                                                                               .build()))
                                                                                       .build();

```

Notice that we define an S3 origin and a custom origin. The S3 origin points at our bucket and our API is pointed to by the custom origin. CloudFront takes care of working out which requests should go where.

Finally, let's define the content of our website - we want changes made in this repository to not only change the underlying infrastructure, but also for the content of our website to change too.

```java
BucketDeployment websiteContent = BucketDeployment.Builder.create(this, "WebsiteContent")
                                                          .destinationBucket(websiteBucket)
                                                          .sources(List.of(Source.asset(stackConfig.getWebsiteAssetsPath())))
                                                          .distribution(cloudFrontWebDistribution)
                                                          .distributionPaths(List.of("/*"))
                                                          .memoryLimit(2048)
                                                          .build();

```

This CDK construct is all we need to have our code updated. We define a reasonable memory size for the lambda that will perform the copy of the data as if it's too small and we have a large website with lots of files to copy, the Lambda will timeout before completing.


Full details of this are in GitHub where the source to my blog is stored [here](https://github.com/john-tipper/johntipper.org). The next post will cover integrating the build and deploy of the website into CI/CD using GitHub Actions.
