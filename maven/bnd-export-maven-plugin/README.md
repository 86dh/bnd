# bnd-export-maven-plugin

The `bnd-export-maven-plugin` is a bnd based plugin to export bndrun files.

## What does the `bnd-export-maven-plugin` do?

Point the plugin to a bndrun file in the same project. It will export a runnable jar.

```
    <plugin>
        <groupId>biz.aQute.bnd</groupId>
        <artifactId>bnd-export-maven-plugin</artifactId>
        <version>${bnd.version}</version>
        <configuration>
            <failOnChanges>false</failOnChanges>
            <bndruns>
                <bndrun>mylaunch.bndrun</bndrun>
            </bndruns>
        </configuration>
        <executions>
            <execution>
                <goals>
                    <goal>export</goal>
                </goals>
            </execution>
        </executions>
    </plugin>
```

Here's an example setting the `bundles` used for resolution.

```
    ...
    <configuration>
        ...
        <bundles>
            <bundle>bundles/org.apache.felix.eventadmin-1.4.8.jar</bundle>
            <bundle>bundles/org.apache.felix.framework-5.4.0.jar</bundle>
        </bundles>
    </configuration>
    ...
```

## Configuration Properties

|Configuration Property | Description |
| ---                   | ---         |
|`bndruns`              | Contains at least one `bndrun` child element, each element naming a bndrun file defining a runtime and tests to execute against it.|
|`targetDir`            | The director into which to export the result. _Defaults to `${project.build.directory}`._|
|`resolve`              | Whether to resolve the `-runbundles` required for a valid runtime. _Defaults to `false`._|
|`failOnChanges`        | Whether to fail the build if any change in the resolved `-runbundles` is discovered. _Defaults to `true`._|
|`bundlesOnly`          | Instead of creating an executable jar place runbundles into `targetDir`. _Defaults to `false`._|
|`bundles`              | This is the collection of files to use for locating bundles during the bndrun resolution. Paths are relative to `${project.basedir}` by default. Absolute paths are allowed. _Defaults to dependencies in the `compile` and `runtime`, plus the current artifact (if any)._|
|`useDefaults`          | If `true` adds `bundles` to the normally calculated defaults rather than replacing them. _Defaults to `true`._|
