package org.johntipper.blog.aws.cdk.webapp;

import org.jetbrains.annotations.Nullable;
import software.amazon.awscdk.core.*;
import software.amazon.awscdk.services.iam.CompositePrincipal;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.ssm.StringParameter;

import java.util.List;

public class LambdaEdgeCloudFrontRewriteStack extends Stack {

    public LambdaEdgeCloudFrontRewriteStack(@Nullable Construct scope, @Nullable String id, @Nullable StackProps props, WebBackendStackConfig stackConfig) {
        super(scope, id, props);

        Role edgeRole = Role.Builder.create(this, "EdgeRole")
                                    .assumedBy(new CompositePrincipal(ServicePrincipal.Builder.create("lambda.amazonaws.com")
                                                                                              .build(),
                                                                      ServicePrincipal.Builder.create("edgelambda.amazonaws.com")
                                                                                              .build()))
                                    .managedPolicies(List.of(ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole")))
                                    .build();

        Function lambdaEdgeFunction = Function.Builder.create(this, "LambdaEdgeFunction")
                                                      .description("LambdaEdge function to rewrite directory requests")
                                                      .code(Code.fromAsset(stackConfig.getLambdaEdgeLambdaPath()))
                                                      .handler("index.handler")
                                                      .timeout(Duration.seconds(5))
                                                      .runtime(Runtime.NODEJS_12_X)
                                                      .memorySize(128)
                                                      .role(edgeRole)
                                                      .functionName(PhysicalName.GENERATE_IF_NEEDED)
                                                      .logRetention(RetentionDays.ONE_DAY)
                                                      .build();

        StringParameter lambdaEdgeLambdaVersion = StringParameter.Builder.create(this, "LambdaEdgeLambdaVersion")
                                                                         .parameterName("/blog/lambdaEdgeLambdaVersion")
                                                                         .description("CDK parameter stored for cross region Edge Lambda")
                                                                         .stringValue(lambdaEdgeFunction.getCurrentVersion().getFunctionArn())
                                                                         .build();

    }


}
