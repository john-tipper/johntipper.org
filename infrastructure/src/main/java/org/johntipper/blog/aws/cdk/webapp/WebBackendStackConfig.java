package org.johntipper.blog.aws.cdk.webapp;

import org.apache.commons.cli.CommandLine;

public class WebBackendStackConfig {

    public static final String API_LAMBDA_PATH_KEY = "apiLambdaPath";
    public static final String WEB_ASSETS_KEY = "webAssets";
    public static final String TARGET_ACCOUNT_KEY = "targetAccount";
    public static final String REGION_KEY = "region";
    public static final String DOMAIN_NAME_KEY = "domainName";
    public static final String LAMBDA_EDGE_LAMBDA_PATH_KEY = "lambdaEdge";

    private final String domainName;

    private final String apiLambdaPath;

    private final String lambdaEdgeLambdaPath;

    private final String targetAccount;


    private final String region;


    public WebBackendStackConfig(String domainName, String apiLambdaPath, String targetAccount, String region, String lambdaEdgeLambdaPath) {
        this.domainName = domainName;
        this.apiLambdaPath = apiLambdaPath;
        this.targetAccount = targetAccount;
        this.region = region;
        this.lambdaEdgeLambdaPath = lambdaEdgeLambdaPath;
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

    public String getLambdaEdgeLambdaPath() {
        return lambdaEdgeLambdaPath;
    }

    public static WebBackendStackConfig fromCommandLine(CommandLine cmd) {
        return new WebBackendStackConfig(cmd.getOptionValue(DOMAIN_NAME_KEY), cmd.getOptionValue(API_LAMBDA_PATH_KEY), cmd.getOptionValue(TARGET_ACCOUNT_KEY), cmd.getOptionValue(REGION_KEY), cmd.getOptionValue(LAMBDA_EDGE_LAMBDA_PATH_KEY));
    }
}