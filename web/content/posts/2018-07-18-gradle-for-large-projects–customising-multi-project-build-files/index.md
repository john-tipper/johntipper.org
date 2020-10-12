---
title: "Gradle for large projects – customising multi-project build files"
author: John Tipper
date: 2018-07-18
hero: ./images/Scan10002.JPG
excerpt: Customising Gradle multi-project builds by renaming the build.gradle file to be named after the project itself.
---

**Gradle** supports multi-project builds, in which each project, or module, located underneath a single directory, is a separate Gradle project.  Here is an example of such a directory structure, taken from the documentation:

```
water/
    build.gradle
    settings.gradle
    bluewhale/
        build.gradle
    krill/
        build.gradle
```

Note however, that each project has its own `build.gradle`.  When you have many modules in your project and you have several of the `build.gradle` files open in your IDE, if they are similar then it can quickly become a nightmare trying to work out which one is which (you’ll generally have the titlebar showing part of a path and the filename, all of which look very similar).  Wouldn’t it be good to have a different name for each module with a single `build.gradle` file at the top level to tie everything together?  Something like the below, perhaps?

```
water/
    build.gradle
    settings.gradle
    bluewhale/
        bluewhale.gradle
    krill/
        krill.gradle
```

The answer is to turn to your `settings.gradle` file.

```groovy
// settings.gradle
include "bluewhale", "krill"
rootProject.name = "water"
rootProject.children.each { child ->
    def moduleName = child.name
    child.name = "${rootProject.name}-${moduleName}"
    child.buildFileName = "${moduleName}.gradle"
}
```
