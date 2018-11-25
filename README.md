# JavaDoc package-list generator

It was found in the migration to jdk10 that the maven javadoc plugin was not creating the `package-list` file as part of it's normal build.
As a result, the build log was outputting many errors of the form:

```bash
[ERROR] Error fetching link: /Users/.../light-4j/config/target/apidocs/package-list. Ignored it.
```

The build seemed to be only generating the `element-list` file.

To resolve the issue with javadoc links, and remove the errors from the logs, this plugin can be used to generate the `package-list`
file dynamically based on a scan of the source directories of submodules.

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

