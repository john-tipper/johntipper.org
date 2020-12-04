---
title: AWS CDK cross-account deployments with CDK Pipelines and cdk-assume-role-credential-plugin
author: John Tipper
date: 2020-12-04
hero: ./images/IMG_0456.jpeg
excerpt: Cross-account deployments with AWS CDK, CDK Pipelines and cdk-assume-role-credential-plugin
---

# Cross-account deployments with AWS CDK and CDK Pipelines and cdk-assume-role-credential-plugin

AWS CDK is really very nice for the speed with which you can create lots of infrastructure in a reusable fashion. However, for a long time, performing cross-account deployments was rather painful when some stacks had to go to one account and other to a different account, because getting the right credentials to CDK for the different accounts was difficult. Woe betide you if you wanted to do lots of deployments to very many accounts.  This post will demonstrate how to use an AWS plugin for CDK called `cdk-assume-role-credential-plugin` to make life easy.

## What we want to achieve: a fully automated build pipeline

We'll assume that you have 2 accounts into which you would like to deploy some infrastructure, where that infrastructure has been defined using CDK.  We'll also assume that the project that where that CDK infrastructure exists is more than just CDK: maybe you have some other compilation steps required as part of the deployment process. An example might be where you are compiling some Lambdas and you then want to use those compiled binaries in a CDK deployment. We'll do the deploy in a generic CodeBuild project which could perform other steps if you wish, in addition to doing the deployment.

We want our build and deployment of our project to be fully automated. When we decide we want to modify the steps inside our build and deployment pipeline, we'd like these changes to the pipeline to be automated too.

We'll also assume that the account where your CI/CD pipeline is running is in a different account to the accounts where you want to deploy your infrastructure. This means 3 sets of credentials we need to deal with, but this could easily be many more if you have lots of accounts where you wish to deploy to.

We don't want to have to tinker around with project settings and dependencies: we'd like this to be done for us with minimal setup.

## Prerequisites

In order to use CDK, we need to have bootstrapped the accounts and regions to which we want to deploy stuff. Bootstrapping is defined [here](https://docs.aws.amazon.com/cdk/latest/guide/bootstrapping.html), but there is also some useful information in the [CDK design documention](https://github.com/aws/aws-cdk/blob/master/design/cdk-bootstrap.md) on GitHub which is not in the AWS documentation.

The act of bootstrapping creates some infrastructure in the account and region that is targeted.  There are 2 styles of bootstrapping: legacy and new. The legacy way is still the default and create just a S3 bucket into which assets are published when deploying, but the new way creates some additional resources, such as an ECR repository (for storing Docker images that are the result of building Docker assets) and IAM roles which may be assumed by CDK when synthesizing and deploying resources.  We need the **new** way of bootstrapping, and because this is not yet the default for CDK, there are a few extra arguments to use.

For each account/region pair into which we wish to deploy, we need to run the following command:
```shell script
cdk bootstrap --trust <trusted account id>[,<trusted account id>...] --cloudformation-execution-policies arn:aws:iam::aws:policy/AdministratorAccess aws://<target account id>/<region>
```

What this command is doing is saying that each `<trusted account id>` in the list will be allowed to assume particular IAM roles within the target account (`<target account id>`), called the Publishing and Deployment Action Roles when writing assets to S3 or ECR or executing changesets. That role will have some permissions associated with uploading assets to CDK buckets and creating and starting changesets, but it won't, in and of itself, be able to do very much. When CloudFormation runs the changeset, it needs to create and mutate infrastructure, so needs a fairly broad set of permissions: quite how broad depends on what you want to be able to manage with CDK. In the command above, we are giving an Access-All-Areas pass to CloudFormation (the AWS service, not the identity calling CDK), and you may wish to de-scope this if you don't want CDK/CloudFormation to be able to do everything in the target account.

There are 2 stages to a CDK deployment: synthesis followed by deployment. As part of the synthesis of a Cloud Assembly, the user may specify context lookups. An example might be to query Route53 HostedZone details by way of `HostedZone.fromLookup()`, for instance. In order to execute the lookup, the user running CDK synth requires AWS credentials and these credentials need to be scoped to the target account.  Calling `cdk deploy` will also cause a synthesis to happen first before deployment, unless the user passes a path to an already synthesized Cloud Assembly by means of the `--app /path/to/cdk.out`, so it may also require AWS credentials.  We will use a CDK plugin called [`cdk-assume-role-credential-plugin`](https://github.com/aws-samples/cdk-assume-role-credential-plugin) to retrieve credentials for us, but we need to tell this plugin what role to assume when retrieving STS credentials by way of `sts:AssumeRole`.  By default, the plugin will look for a role called `cdk-readOnlyRole` to fetch context.  That role does not exist, so we need to either create it, or provide another role which has sufficient read privileges in order to satisfy any CDK context lookups we wish to permit.

For the purposes of this example, we will assume a role called `cdk-readOnlyRole` exists in each of our 2 target accounts, where those accounts trust our CI/CD account, i.e. an appropriately permissioned user inside our CI/CD account may call `sts:AssumeRole` on that role in the target accounts.  How you create this is up to you: there is an example inside the [cdk-assume-role-credential-plugin repository on GitHub](https://github.com/aws-samples/cdk-assume-role-credential-plugin/tree/main/cdk-sample-pipelines-app), look for the `required-resources.ts` files. That example uses CDK to create a stack which defines the role which is given an AWS managed policy called `ReadOnlyAccess`.  If you are automatically creating accounts into which you wish your CI/CD account to be able to deploy, you'll probably create these roles at this point right after creating the account. Remember, there is a role called `OrganizationAccountAccessRole` in each sub account which is assumable by the master/admin account of the AWS Organization which has admin permissions, so you might use this to create your read-only roles if you wish. 

 
## Project setup

We will use [Projen](https://github.com/projen/projen) to create and manage our project. It is not a templating tool, where the generated templates then immediately start to rot. Projen generates your project definition files for you, but all management of these is done through Projen. Re-running projen regenerates the files for you.

Install projen:
```shell script
npm install -g projen
```

We will create a monorepo with 2 subprojects: one for our build pipeline and another for our project itself (i.e. the infrastructure we wish to actually deploy to the other accounts).

```shell script
mkdir example
cd example
git init

# directory for our build pipeline
mkdir pipeline
pushd pipeline
projen new awscdk-app-ts
popd 

# directory for our project
mkdir infra
pushd infra
projen new awscdk-app-ts
popd 
```

### Build Pipeline

We now have a lot of boilerplate created for us that would otherwise take ages to do (or at the very least, it would take me ages). Let's configure our build pipeline:

```javascript
// within pipeline/.projenrc.js
const project = new AwsCdkTypeScriptApp({
//...
cdkDependencies: [
        '@aws-cdk/pipelines',
        '@aws-cdk/aws-codepipeline',
        '@aws-cdk/aws-codepipeline-actions',
        '@aws-cdk/aws-codebuild',
        '@aws-cdk/aws-iam',
        '@aws-cdk/aws-s3',
        '@aws-cdk/aws-logs',
    ],
context: {
        '@aws-cdk/core:newStyleStackSynthesis': true,
    },
devDeps: [
        'cdk-assume-role-credential-plugin@^1.2.1',
    ],  
//...
}
project.cdkConfig.plugin = ["cdk-assume-role-credential-plugin"];

project.synth();
```

Note that we are adding in some CDK dependencies which we will use to define our build pipeline and the `cdk-assume-role-credential-plugin` as a dev dependency. We want a minimum version of `1.2.1` as there was a race condition I cam across in `1.2.0` which caused the plugin to misbehave under some circumstances. We apply that plugin by means of the call to `project.cdkConfig.plugin=`.

Also note that we set a context value: this will be added to the `cdk.json` file when we run projen. We are telling CDK that we are using the new-style bootstrapping.

Now let's install our dependencies:

```shell script
pushd pipeline
npx projen
```

Now let's define our build pipeline. Firstly, let's define a policy for a role which our pipeline will assume:

```typescript
// src/lib/PipelinePolicyDocument
import { Effect, PolicyDocument, PolicyStatement } from '@aws-cdk/aws-iam';

export interface PipelinePolicyProps {
  account: string;
  region: string;
}

export class PipelinePolicyDocument extends PolicyDocument {

  constructor(props: PipelinePolicyProps) {
    super({
      statements: [
        new PolicyStatement({
          sid: 'CloudWatchLogsPolicy',
          effect: Effect.ALLOW,
          actions: [
            'logs:CreateLogGroup',
            'logs:CreateLogStream',
            'logs:PutLogEvents',
          ],
          resources: ['*'],
        }),
        new PolicyStatement({
          sid: 'S3GetObjectPolicy',
          effect: Effect.ALLOW,
          actions: [
            's3:GetObject',
            's3:GetObjectVersion',
          ],
          resources: ['*'],
        }),
        new PolicyStatement({
          sid: 'S3ListBucketPolicy',
          effect: Effect.ALLOW,
          actions: [
            's3:ListBucket',
          ],
          resources: ['*'],
        }),
        new PolicyStatement({
          sid: 'S3PutObjectPolicy',
          effect: Effect.ALLOW,
          actions: [
            's3:PutObject',
          ],
          resources: ['*'],
        }),
        new PolicyStatement({
          sid: 'S3BucketIdentity',
          effect: Effect.ALLOW,
          actions: [
            's3:GetBucketAcl',
            's3:GetBucketLocation',
          ],
          resources: ['*'],
        }),
        new PolicyStatement({
          sid: 'AccessGitHubPublishSecret',
          effect: Effect.ALLOW,
          actions: [
            'ssm:GetParameter',
            'ssm:GetParameters',
          ],
          resources: [
            `arn:aws:ssm:${props.region}:${props.account}:parameter/path/to/my/token`,
          ],
        }),
        new PolicyStatement({
          sid: 'DecryptGitHubSecrets',
          effect: Effect.ALLOW,
          actions: [
            'kms:Decrypt',
          ],
          resources: [
            '*',
          ],
        }),
        new PolicyStatement({
          sid: 'AssumeCDKReadonlyRole',
          effect: Effect.ALLOW,
          actions: [
            'sts:AssumeRole',
          ],
          resources: [
            'arn:aws:iam::*:role/cdk-readOnlyRole',
            'arn:aws:iam::*:role/cdk-hnb659fds-deploy-role-*',
            'arn:aws:iam::*:role/cdk-hnb659fds-file-publishing-*',
          ],
        }),
      ],
    });
  }
}
```
The above is just a lot of boilerplate and is basically copied from any example you'll find in the AWS docs for CDK Pipelines. **The last stanza is important** though, and doesn't appear fully in the docs. Remember, our pipeline needs to be able to perform context lookups, publish assets to S3 prior to deploying and then actually execute the CDK deployment.  It's really important you add these.

Now let's define the Pipeline stack for the master branch of our repo (replace `OWNER` and `REPO` as required):
```typescript
// src/lib/PipelineStack
import { Artifact } from '@aws-cdk/aws-codepipeline';
import { GitHubSourceAction } from '@aws-cdk/aws-codepipeline-actions';
import { Role, ServicePrincipal } from '@aws-cdk/aws-iam';
import { Construct, SecretValue, Stack, StackProps } from '@aws-cdk/core';
import { CdkPipeline, SimpleSynthAction } from '@aws-cdk/pipelines';
import { PipelinePolicyDocument } from './PipelinePolicyDocument';

export class PipelineStack extends Stack {

  pipeline: CdkPipeline;

  sourceArtifact: Artifact;
  cloudAssemblyArtifact: Artifact;
  buildRole: Role;

  sourceAction: GitHubSourceAction;

  constructor(scope: Construct, id: string, props?: StackProps) {
    super(scope, id, props);

    this.sourceArtifact = new Artifact();
    this.cloudAssemblyArtifact = new Artifact();

    this.sourceAction = new GitHubSourceAction({
      actionName: 'GitHub',
      output: this.sourceArtifact,
      oauthToken: SecretValue.secretsManager('/path/to/my/token', {
        jsonField: 'token',
      }),
      owner: 'OWNER',
      repo: 'REPO',
      branch: 'master',
      variablesNamespace: 'SourceVariables',
    });

    this.buildRole = new Role(this, 'CodeBuildRole', {
      roleName: id + '-role',
      assumedBy: new ServicePrincipal('codebuild.amazonaws.com'),
      inlinePolicies: {
        'codebuild-policy': new PipelinePolicyDocument({
          account: (!props || !props.env || !props.env.account) ? '' : props.env.account,
          region: (!props || !props.env || !props.env.region) ? '' : props.env.region,
        }),
      },
    });

    this.pipeline = new CdkPipeline(this, 'Pipeline', {
      cloudAssemblyArtifact: this.cloudAssemblyArtifact,

      sourceAction: this.sourceAction,

      synthAction: SimpleSynthAction.standardYarnSynth({
        sourceArtifact: this.sourceArtifact,
        cloudAssemblyArtifact: this.cloudAssemblyArtifact,
        subdirectory: 'pipeline',

        installCommand: 'yarn --cwd pipeline install --frozen-lockfile && yarn --cwd pipeline projen',
        buildCommand: 'yarn --cwd pipeline run build',
        synthCommand: 'yarn --cwd pipeline cdk synth',
      }),
    });
  }
}
```

In this stack, we define a basic CDK pipeline. It will build a CodePipeline with 2 stages: a source stage which links to GitHub and will be triggered automatically by webhooks whenever a push occurs. This source stage assumes that there is a secret in the Secret Manager under the path `/path/to/my/token`. Note that the permissions for the Role allow this token to be retrieved.  We also define a build stage, which will call CDK synth and deploy to build our pipeline.

So far, if we deployed this pipeline, it would build and redeploy itself and whilst this is cool, it's not that useful, so let's add another stage where we actually do some work.

```typescript
// src/lib/MyCodeBuild.ts
import { BuildSpec, ComputeType, LinuxBuildImage, PipelineProject } from '@aws-cdk/aws-codebuild';
import { Artifact } from '@aws-cdk/aws-codepipeline';
import { CodeBuildAction, GitHubSourceAction } from '@aws-cdk/aws-codepipeline-actions';
import { Role } from '@aws-cdk/aws-iam';
import { LogGroup, RetentionDays } from '@aws-cdk/aws-logs';
import { Construct, Duration } from '@aws-cdk/core';

export interface MyCodeBuildProps {
  id: string;
  parent: Construct;
  input: Artifact;
  buildRole: Role;
}

const buildSpec = {
  version: '0.2',
  phases: {
    install: {
      'runtime-versions': {
        nodejs: '10',
      },
      'commands': [
        'npm install -g aws-cdk cdk-assume-role-credential-plugin',
      ],
    },
    build: {
      commands: [
        // add whatever build command you want here 
        'yarn --cwd infra run build',
        'pushd infra && cdk deploy --app cdk.out/ --require-approval never "*" && popd',
      ],
    },
  },
};


export class MyCodeBuild {

  codeBuildAction: CodeBuildAction;

  constructor(props: MyCodeBuildProps) {

    // some log group - name it as you see fit and retain the logs for as long as needed
    const logGroup = new LogGroup(props.parent, props.id + '-loggroup', {
      logGroupName: `/aws/codebuild/${props.id}`,
      retention: RetentionDays.ONE_WEEK,
    });

    // change this as required
    this.codeBuildAction = new CodeBuildAction({
      actionName: 'ReleaseMyInfra',
      input: props.input,
      project: new PipelineProject(props.parent, props.id, {
        role: props.buildRole,
        environment: {
          buildImage: LinuxBuildImage.AMAZON_LINUX_2_3,
          computeType: ComputeType.SMALL,
          privileged: true,
        },
        buildSpec: BuildSpec.fromObject(buildSpec),
        logging: {
          cloudWatch: {
            logGroup: logGroup,
            enabled: true,
          },
        },
      }),
    });
  }
}
```

In the above, we define a CodeBuild stage which uses the same permissions as we defined earlier. We accept as input some artifact, which we will define later.  When the build runs, it installs onto the Nodejs environment the `aws-cdk` and `cdk-assume-role-credential-plugin`.

We now need to integrate this latter stage into our pipeline.

```typescript
import { App } from '@aws-cdk/core';
import { PipelineStack } from './lib/PipelineStack';
import { MyCodeBuild } from './lib/MyCodeBuild';

const app = new App();

// define where our CI/CD environment will run
const account = '123456789012';
const region = 'eu-west-1';

const pipelineStack = new PipelineStack(app, 'MyPipeline', {
  env: {
    account: account,
    region: region,
  },
});

const releaseMyInfra = new MyCodeBuild({
  id: 'ReleaseMyInfra',
  parent: pipelineStack.pipeline,
  input: pipelineStack.sourceArtifact,
  buildRole: pipelineStack.buildRole,
});

// note that we add a normal CodeBuild stage here, but we can use addApplicationStage if we just want to build and deploy a pure CDK application
// we can pass different build artifacts to the latter stages if we wish, there's not always a need to pass the entire source code
// checkout to this stage as an input
releaseMyInfra.addActions(releaseMyInfra.codeBuildAction);

app.synth();
```

In the above, we add our newly defined CodeBuild stage into our pipeline, which will execute after the pipeline has built itself. This concludes the definition of our pipeline.

### Our CDK Project

We now need to look at our infrastructure project, which is the project we want to build and deploy.  I'm not going to go into a great deal of detail here: you can create anything you want in the same way that you created the CDK pipeline. I'm just going to cover the minimum to set it up.

```javascript
// within infra/.projenrc.js
const project = new AwsCdkTypeScriptApp({
//...
cdkDependencies: [
    // some CDK dependencies here, whatever you need for your project
    ],
context: {
        '@aws-cdk/core:newStyleStackSynthesis': true,
    },
devDeps: [
        'cdk-assume-role-credential-plugin@^1.2.1',
    ],  
//...
}
project.cdkConfig.plugin = ["cdk-assume-role-credential-plugin"];

project.synth();
```

Again, let's install our dependencies:

```shell script
pushd pipeline
npx projen
```

Let's create two stacks that exist in two different accounts:

```typescript
const app = new App();

const stack1 = new ImaginaryStack(app, 'MyStackInAccount1', {
  env: {
    account: '111111111111',
    region: 'eu-west-1',
  },
});

const stack2 = new ImaginaryStack(app, 'MyStackInAccount2', {
  env: {
    account: '222222222222',
    region: 'us-east-1',
  },
});

app.synth();
```

You can now iterate in the standard fashion: calling `yarn run build` will build and run your tests, then perform a cdk synth. You will need AWS credentials if you perform context lookups as part of your synth.

## Deployment

When we are ready, we can commit and push to Git.

```shell script
git add .
git commit -m "Initial commit"
git push origin master
```

Note that this will have no effect inside AWS yet, as we have not deployed anything. We now need to perform an initial seed deployment to AWS. Assuming that the build of the pipeline completes, successfully, our pipeline will becomes self aware and even if the latter stages fail, whenever we push changes to our Git repository, our pipeline will rebuild, redeploy itself, then perform whatever build stages we have defined, which for our example, is the deployment of more CDK infrastructure.

The initial deployment of our pipeline needs to be done with credentials that permit the user to deploy the pipeline to the CI/CD account:

```shell script
pushd pipeline
cdk deploy --profile my-profile-allowing-context-lookups-and-deployments
```

The call to CDK deploy is something that you will need to riff on: it's highly dependent on the permissions you have defined within your CI/CD account.

What you don't see in the normal logs is the heavy lifting that is being done for you by `cdk-assume-role-credential-plugin`: for each stack, it will retrieve credentials if the standard ones won't suffice for the target accounts (`111111111111` and `222222222222`) by assuming the `arn:aws:iam::*:role/cdk-hnb659fds-deploy-role-*` and `arn:aws:iam::*:role/cdk-hnb659fds-file-publishing-*` roles in the target accounts to publish CDK assets as required then create and execute the changesets.

## Troubleshooting

If you run into issues, then they are likely to be associated with incorrect permissions. You can turn on additional logging by mutating your pipeline (just make the changes, then push: the pipeline will take care of rebuilding itself) to add logging to either the pipeline or infra project buildscripts (or both):

```typescript
// within the pipeline buildscript
    'yarn --cwd pipeline run build --debug -v -v -v',

// within the infra project buildscript
    'yarn --cwd infra run build  --debug -v -v -v',
```

You'll now have pretty verbose logs which should assist with tracking down any issues.

