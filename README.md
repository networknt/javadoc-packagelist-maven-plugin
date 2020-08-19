# JavaDoc package-list generator

It was found in the migration to jdk10 that the maven javadoc plugin was not creating the `package-list` file as part of it's normal build.
As a result, the build log was outputting many errors of the form:

```bash
[ERROR] Error fetching link: /Users/.../light-4j/config/target/apidocs/package-list. Ignored it.
```

The build was only generating the `element-list` file instead of the `package-list` that the tools used to expect. Although recent JDKs support
linking to `element-list`, there are interoperability issues with earlier versions.

To resolve the issue with javadoc links and remove the errors from the logs, this plugin can be used to generate the `package-list` file
dynamically. First, it attempts to get the exported packages from the `module-info` file, if it exists. If that fails, then scans the source
directories to extract the package names.

Packages that were excluded by a
[`excludePackageNames`](https://maven.apache.org/plugins/maven-javadoc-plugin/javadoc-mojo.html#excludePackageNames) in the
`maven-javadoc-plugin` configuration are not included in the file.

The plugin should be bound to the `package` phase, and included as follows:

```xml
<plugin>
    <groupId>com.networknt</groupId>
    <artifactId>javadoc-packagelist-maven-plugin</artifactId>
    <version>${javadoc.packagelist.version}</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>gen-package-list</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

The file is created at the place where the javadocs are produced, either the default `apidocs` location or the directory specified by `destDir`
in the `maven-javadoc-plugin` configuration.
