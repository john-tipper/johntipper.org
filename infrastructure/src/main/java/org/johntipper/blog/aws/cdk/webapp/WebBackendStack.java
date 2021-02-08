package org.johntipper.blog.aws.cdk.webapp;

import software.amazon.awscdk.core.*;
import software.amazon.awscdk.customresources.AwsCustomResource;
import software.amazon.awscdk.customresources.AwsCustomResourcePolicy;
import software.amazon.awscdk.customresources.AwsSdkCall;
import software.amazon.awscdk.customresources.PhysicalResourceId;
import software.amazon.awscdk.services.certificatemanager.DnsValidatedCertificate;
import software.amazon.awscdk.services.cloudfront.*;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Version;
import software.amazon.awscdk.services.route53.*;
import software.amazon.awscdk.services.route53.patterns.HttpsRedirect;
import software.amazon.awscdk.services.route53.targets.CloudFrontTarget;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class WebBackendStack extends Stack {
    public WebBackendStack(Construct scope, String id, StackProps props, WebBackendStackConfig stackConfig) throws IOException {
        super(scope, id, props);

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

        // commented out purely so I don't have to pay someone deciding to hit my endpoint in a for loop...
//        HelloWorldApi helloWorldApi = new HelloWorldApi(this, "HelloWorldApi", props, stackConfig);

        // S3 bucket we'll use for storing our website in
        Bucket websiteBucket = Bucket.Builder.create(this, "WebsiteBucket")
                                             .bucketName(String.format("website-%s", props.getEnv().getAccount()))
                                             .encryption(BucketEncryption.UNENCRYPTED)
                                             .websiteIndexDocument("index.html")
                                             .removalPolicy(RemovalPolicy.DESTROY)
                                             .build();

        OriginAccessIdentity webOai = OriginAccessIdentity.Builder.create(this, "WebOai")
                                                                  .comment(String.format("OriginAccessIdentity for %s", stackConfig.getDomainName()))
                                                                  .build();


        websiteBucket.grantRead(webOai);

        AwsCustomResource lambdaParameter = AwsCustomResource.Builder.create(this, "LambdaParameter")
                                                                     .policy(AwsCustomResourcePolicy.fromStatements(List.of(
                                                                         PolicyStatement.Builder.create()
                                                                                                .effect(Effect.ALLOW)
                                                                                                .actions(List.of("ssm:GetParameter*"))
                                                                                                .resources(List.of(formatArn(ArnComponents.builder()
                                                                                                                                          .service("ssm")
                                                                                                                                          .region("us-east-1")
                                                                                                                                          .resource("parameter/blog/lambdaEdgeLambdaVersion")
                                                                                                                                          .build())))
                                                                                                .build())))
                                                                     .onUpdate(AwsSdkCall.builder()
                                                                                         .service("SSM")
                                                                                         .action("getParameter")
                                                                                         .parameters(Map.of("Name", "/blog/lambdaEdgeLambdaVersion"))
                                                                                         .region("us-east-1")
                                                                                         .physicalResourceId(PhysicalResourceId.of(new Date().toString()))
                                                                                         .build())
                                                                     .build();

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
                                                                                                                                                                    .lambdaFunctionAssociations(List.of(
                                                                                                                                                                        LambdaFunctionAssociation.builder()
                                                                                                                                                                                                 .eventType(LambdaEdgeEventType.VIEWER_REQUEST)
                                                                                                                                                                                                 .lambdaFunction(Version.fromVersionArn(this, "EdgeLambdaVersion", lambdaParameter.getResponseField("Parameter.Value")))
                                                                                                                                                                                                 .build()
                                                                                                                                                                    ))
                                                                                                                                                                    .build()))
                                                                                                                                         .s3OriginSource(S3OriginConfig.builder()
                                                                                                                                                                       .originAccessIdentity(webOai)
                                                                                                                                                                       .s3BucketSource(websiteBucket)
                                                                                                                                                                       .build())
                                                                                                                                         .build()
                                                                                                                      // commented out putrely so I don't have to pay someone deciding to hit my endpoint in a for loop...
//                                                                                                   ,
//                                                                                                                      SourceConfiguration.builder()
//                                                                                                                                         .behaviors(List.of(Behavior.builder()
//                                                                                                                                                                    .pathPattern("api/*")
//                                                                                                                                                                    .allowedMethods(CloudFrontAllowedMethods.ALL)
//                                                                                                                                                                    .build()))
//                                                                                                                                         .customOriginSource(CustomOriginConfig.builder()
//                                                                                                                                                                               .domainName(String.format("%s.execute-api.%s.amazonaws.com", helloWorldApi.getRestApi().getRestApiId(), stackConfig.getRegion()))
//                                                                                                                                                                               .build())
//                                                                                                                                         .build()
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

    }


}