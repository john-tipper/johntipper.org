package org.johntipper.blog.aws.cdk.webapp;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;

import java.io.IOException;
import java.util.Map;

import static org.johntipper.blog.aws.cdk.webapp.WebBackendStackConfig.*;

public class WebBackendApp {

    private static final Logger LOG = LoggerFactory.getLogger(WebBackendApp.class);

    final App app;

    public WebBackendApp(CommandLine cmd) throws IOException {

        this.app = new App();

        WebBackendStackConfig webStackConfig = WebBackendStackConfig.fromCommandLine(cmd);

        LambdaEdgeCloudFrontRewriteStack lambdaEdgeCloudFrontRewriteStack = new LambdaEdgeCloudFrontRewriteStack(app, "LambdaEdgeCloudFrontRewriteStack",
                                                                                                                 StackProps.builder()
                                                                                                                           .env(Environment.builder()
                                                                                                                                           .account(webStackConfig.getTargetAccount())
                                                                                                                                           .region("us-east-1")
                                                                                                                                           .build())
                                                                                                                           .stackName("LambdaEdgeCloudFrontRewriteStack")
                                                                                                                           .tags(Map.of("cdk", Boolean.toString(true)))
                                                                                                                           .build(),
                                                                                                                 webStackConfig);

        WebBackendStack webBackendStack = new WebBackendStack(app, "WebBackendStack",
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

        options.addOption(Option.builder(LAMBDA_EDGE_LAMBDA_PATH_KEY)
                                .argName(LAMBDA_EDGE_LAMBDA_PATH_KEY)
                                .desc("Path to the Lambda@Edge function for rewriting CloudFront requests.")
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