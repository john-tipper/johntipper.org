# Website via CDK

https://github.com/john-tipper/johntipper.org/workflows/build/badge.svg

## Build

```shell script
pushd web
gatsby clean
gatsby build
popd

./gradlew cdkPrepare

cdk synth \
  --app 'java -jar ./infrastructure/build/cdk/infrastructure-all.jar -apiLambdaPath ./infrastructure/build/cdk/api-lambdas.zip -webAssets ./infrastructure/build/cdk/web -domainName johntipper.org -region eu-west-2 -targetAccount 502171377804' \
  --profile blog \
  --output build/cdk.out 

cdk deploy --app ./build/cdk.out --profile blog --require-approval never "*"

```