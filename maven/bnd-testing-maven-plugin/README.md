# bnd-testing-maven-plugin

The `bnd-testing-maven-plugin` is a bnd based plugin to run integration tests.

## What does the `bnd-testing-maven-plugin` do?

Point the plugin at one or more bndrun files in the same project. It will execute tests against the
runtime defined in the bndrun file.

The bndrun file must contain bundles that have the `Test-Cases` header set to class names that
contain the JUnit tests.

Here is an example configuration:
```
            <plugin>
                <groupId>biz.aQute.bnd</groupId>
                <artifactId>bnd-testing-maven-plugin</artifactId>
                <version>${bnd.version}</version>
                <configuration>
                    <failOnChanges>false</failOnChanges>
                    <bndruns>
                        <bndrun>mytest.bndrun</bndrun>
                    </bndruns>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>testing</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
```

## Configuration Properties

|Configuration Property          | Description |
| ---                            | ---         |
|`bndruns`                       | Contains at least one `bndrun` child element, each element naming a bndrun file defining a runtime and tests to execute against it.|
|`resolve`                       | Whether to resolve the `-runbundles` required for a valid runtime. _Defaults to `false`._|
|`failOnChanges`                 | Whether to fail the build if any change in the resolved `-runbundles` is discovered. _Defaults to `true`._|
|`reportsDir`                    | The output directory for test reports. A subdirectory of `${bndrun}` will be created for each bndrun file supplied. _Defaults to `${project.build.directory}/test-reports`._|
|`cwd`                           | The current working directory of the test process. A subdirectory of `${bndrun}` will be created for each bndrun file supplied. _Defaults to `${project.build.directory}/test`._|
|`skipTests` OR `maven.test.skip`| Does not execute any tests. Used from the command line via `-D`. _Defaults to `false`._|
|`testingSelect`                 | A file path to a test file, overrides anything else. _Defaults to `${testing.select}`._ Override with property `testing.select`.|
|`testing`                       | A glob expression that is matched against the file name of the listed bndrun files. _Defaults to `${testing}`._ Override with property `testing`.|

