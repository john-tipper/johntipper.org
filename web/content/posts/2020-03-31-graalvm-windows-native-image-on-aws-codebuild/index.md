---
title: GraalVM Windows Native Image on AWS CodeBuild
author: John Tipper
date: 2020-03-31
hero: ./images/Scan10005.JPG
excerpt: How to build a Windows native image executable using GraalVM on AWS CodeBuild.
---

GraalVM is, according to its website ([graalvm.org](https://graalvm.org)):

> a universal virtual machine for running applications written in JavaScript, Python, Ruby, R, JVM-based languages like Java, Scala, Groovy, Kotlin, Clojure, and LLVM-based languages such as C and C++.

Now, where this gets interesting for me is its support for native image compilation.  A normal JVM application requires a VM to execute it: this is provided by the JRE.  As an application developer, either my users need to have this installed in order to run my application, or alternatively I must bundle a JRE with my application.  Tools exist to do this, such as [jpackage](https://docs.oracle.com/en/java/javase/14/docs/specs/man/jpackage.html) or [install4j](https://resources.ej-technologies.com/install4j/help/doc/main/introduction.html), but they all suffer from the same problem: the size of the distribution package.  Also, application start times will be slower, as the JVM starts up.  This latter point may or may not be of interest to you, depending on your use case.

Now native image support basically involves turning Java byte code into native execution instructions – it’s basically a fourth compilation step.  You get an executable that is compiled for a target OS, so you lose the “write once, run anywhere” behaviour of Java, but you no longer have to worry about whether an appropriate JRE is installed on your users’ desktops.  With Java versions changing so quickly now, this could well be a big win if you’re shipping to an enterprise customer, where the software within the company may well be several (many?) Java major versions out of date.  The new version (v20.0) of GraalVM now supports native image compilation for Windows.

GraalVM native image support does not support cross-compilation, meaning if you want to create a Windows executable, you’ll need to compile on a Windows server.  This is where it can get tricky if you use cloud-based CI/CD solutions, as these are often Linux-based and there is little documentation for how to build using GraalVM on Windows.  Such is the case for me when I wanted to build a Windows version of an application within AWS using [CodeBuild](https://aws.amazon.com/codebuild/). AWS supports Windows Server Core 2016 instances (c.f. [here](https://docs.aws.amazon.com/codebuild/latest/userguide/build-env-ref-available.html)), but at the moment these are only available within the US East (N. Virginia), US East (Ohio), US West (Oregon), and Europe (Ireland) regions only.

Here is a brief outline of what I needed to do to get a minimum viable HelloWorld to compile inside CodeBuild. Caveat: I last did any sort of Windows batch programming when I was in my teens, which is several decades ago now, so my PowerShell script could probably be improved.

I was helped enormously by the post here by Michał Sitko: [https://msitko.pl/blog/2020/03/05/native-image-on-windows.html](https://msitko.pl/blog/2020/03/05/native-image-on-windows.html).

Create a CodeBuild project based on a Windows Server, for which you’ll need to be in one of the supported regions.  Commands within the build spec file are run using a PowerShell shell ([https://docs.aws.amazon.com/codebuild/latest/userguide/build-env-ref-cmd.html](https://docs.aws.amazon.com/codebuild/latest/userguide/build-env-ref-cmd.html)).

[Chocolately](https://chocolatey.org/), a package manager for Windows, is already installed in CodeBuild so we use this to install the necessary tools into our build image.

```bash
choco install graalvm visualstudio2017-workload-vctools /Y
```

Then install the native image tooling using the GraalVM updater tool:

```bash
. "C:\Program Files\GraalVM\graalvm-ce-java11-20.0.0\bin\gu.cmd" install native-image
```

Note that the native image tooling requires a native OS toolchain (C++) and this requires a number of environmental variables (PATH, INCLUDE, LIB etc) to be set in order for the compiler to work correctly.  There is a build script that will do this for you that we installed using visualstudio2017-workload-vctools but this script only works using the CMD shell, not the PowerShell that CodeBuild uses.  If you run this script in PowerShell, none of these variables will be visible.  To get around this, we follow the advice [here](https://help.appveyor.com/discussions/questions/18777-how-to-use-vcvars64bat-from-powershell) and write the variables to a file, which we will import into PowerShell in a separate, later, step.

```bash
cmd.exe /c "call `"C:\Program Files (x86)\Microsoft Visual Studio\2017\BuildTools\VC\Auxiliary\Build\vcvars64.bat`" && set > %temp%\vcvars.txt "
```

Now we build our Java application into a jar, such that all dependencies are present, for which I chose Gradle backed up by the [shadow](https://github.com/johnrengelman/shadow) plugin.

```bash
.\gradlew shadowjar
```

Once the jar has been built, we can run the native image tool on it, for which we need to set the appropriate environmental variables that we set earlier.  There is a final gotcha that I found, however. The [AWS docs](https://docs.aws.amazon.com/codebuild/latest/userguide/build-env-ref-cmd.html) state:

>In buildspec version 0.1, CodeBuild runs each Shell command in a separate instance in the build environment. This means that each command runs in isolation from all other commands. Therefore, by default, you cannot run a single command that relies on the state of any previous commands (for example, changing directories or setting environment variables). To get around this limitation, we recommend that you use version 0.2, which solves this issue.

However, I found that if I set the env variables in one step, they were not visible in a subsequent step, even when using build spec version 2.  I therefore had to set the variables and execute the native image build in the same step, like this:

```bash
Get-Content "$env:temp\vcvars.txt" | Foreach-Object { if ($_ -match "^(.*?)=(.*)$") { Set-Content "env:\$($matches[1])" $matches[2] } }; . "C:\Program Files\GraalVM\graalvm-ce-java11-20.0.0\bin\native-image.cmd" --verbose --static --no-fallback -H:+ReportExceptionStackTraces -jar build\libs\GraalVmExample-1.0-SNAPSHOT-all.jar helloworld
```

This will produce an executable called *helloworld.exe*, which I then run as part of the build just to demonstrate that it works.  You should see “Hello world!” in the build logs if all works correctly.

The full `buildspec.yml` looks like this:

```yaml
version: 0.2  
phases:
   install:
     commands:
       - 'choco install graalvm visualstudio2017-workload-vctools /Y'
       - '. "C:\Program Files\GraalVM\graalvm-ce-java11-20.0.0\bin\gu.cmd" install native-image'
       - 'cmd.exe /c "call `"C:\Program Files (x86)\Microsoft Visual Studio\2017\BuildTools\VC\Auxiliary\Build\vcvars64.bat`" && set > %temp%\vcvars.txt "'
   build:
     commands:
       - .\gradlew shadowjar
       - 'Get-Content "$env:temp\vcvars.txt" | Foreach-Object { if ($_ -match "^(.*?)=(.*)$") { Set-Content "env:\$($matches[1])" $matches[2] } }; . "C:\Program Files\GraalVM\graalvm-ce-java11-20.0.0\bin\native-image.cmd" --verbose --static --no-fallback -H:+ReportExceptionStackTraces -jar build\libs\GraalVmExample-1.0-SNAPSHOT-all.jar helloworld'      
       - .\helloworld
```

and the example project is here: [https://github.com/john-tipper/graalvm-helloworld](https://github.com/john-tipper/graalvm-helloworld).