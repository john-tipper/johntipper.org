package org.johntipper.blog.aws.cdk.webapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.apigateway.ApiDefinition;
import software.amazon.awscdk.services.apigateway.IRestApi;
import software.amazon.awscdk.services.apigateway.SpecRestApi;
import software.amazon.awscdk.services.apigateway.StageOptions;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.logs.RetentionDays;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HelloWorldApi extends Construct {

    private IRestApi restApi;

    public HelloWorldApi(@NotNull Construct scope, @NotNull String id, StackProps props, WebBackendStackConfig stackConfig) throws IOException {
        super(scope, id);

        // lambda that we'll use as an example
        SingletonFunction helloWorldLambda = SingletonFunction.Builder.create(this, "HelloWorldLambda")
                                                                      .description("HelloWorld lambda to demonstrate integration with API Gateway")
                                                                      .code(Code.fromAsset(stackConfig.getApiLambdaPath()))
                                                                      .handler(String.format("%s::handleRequest", "org.johntipper.blog.lambda.HelloWorldHandler"))
                                                                      .timeout(Duration.seconds(10))
                                                                      .runtime(Runtime.JAVA_11)
                                                                      .memorySize(256)
                                                                      .uuid(UUID.randomUUID()
                                                                                .toString())
                                                                      .logRetention(RetentionDays.ONE_WEEK)
                                                                      .build();

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

    }

    public IRestApi getRestApi() {
        return restApi;
    }
}
