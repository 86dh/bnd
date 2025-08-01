---
layout: default
title: -includeresource iclause
class: Builder & Executable
summary: |
   Include resources from the file system
note: AUTO-GENERATED FILE - DO NOT EDIT. You can add manual content via same filename in ext folder. 
---

- Example: `-includeresource: lib/=jar/, {preprocess.txt}, 'literal';literal;=true,`

- Pattern: `.*`

<!-- Manual content from: ext/includeresource.md --><br /><br />

The purpose of `-includeresource` is to fill the JAR with non-class resources. In general these come from the file system. For example, today it is very common to have these type of resources in `src/main/resources`. This pattern can easily be simulated by bnd with the `-includeresource` instruction. However, since in OSGi the packaging is so important the `-includeresource` contains a number of options to minimize files on disk and speed up things.

The syntax of the `-includeresource` has become quite complex over time:

    -includeresource ::= iclause ( ',' iclause ) *
    iclause          ::= (unroll | copy) parameter*
    copy             ::= '{' process '}' | process
    process          ::= assignment | source
    assignment       ::= PATH '=' source
    source           ::= ('-')? PATH parameter*
    unroll           ::= '@' (PATH | URL) ( '!/' SELECTOR )?
    parameters       ::= 'flatten:' | 'recursive:' | 'filter:' | `-preprocessmatchers`

In the case of `assignment` or `source`, the PATH parameter can point to a file or directory. It is also possible to use the name.ext path of a JAR file on the classpath, that is, ignoring the directory. The `source` form will place the resource in the target JAR with only the file name, therefore without any path components. That is, including `src/a/b.c` will result in a resource `b.c` in the root of the target JAR.

If the PATH points to a directory, the directory name itself is not used in the target JAR path. If the resource must be placed in a sub directory of the target jar, use the `assignment` form. If the file is not found, bnd will traverse the classpath to see of any entry on the classpath matches the given file name (without the directory) and use that when it matches. The `inline` requires a ZIP or JAR file, which will be completely expanded in the target JAR (except the manifest), unless followed with a file specification. The file specification can be a specific file in the jar or a directory followed by **or *. The** indicates recursively and the* indicates one level. If just a directory name is given, it will mean **.

The `filter:` directive is an optional filter on the resources. This uses the same format as the instructions. Only the file name is verified against this instruction.

    Include-Resource: @osgi.jar,[=\ =]
        {LICENSE.txt},[=\ =]
        acme/Merge.class=src/acme/Merge.class

The `-includeresources` instruction will be merged with all properties that starts with `-includeresources*`.

## Preprocessing

A clause contained in curly braces (`{` `}`) are *preprocessed*. While copying the files are run through the macro processor with the builder providing the properties. In the workspace model, all macros of the project are then available. Well known binary resources (as decided by their extension) are ignored. You can override the extension list with the `-preprocessmatchers` instruction. This must be a a selector that takes the source file name as the input. The clause can also specify a local  `-preprocessmatchers`. This selector is *prepended* to the either the default pre process matchers or the set pre process matchers. This allows for the selection or rejection of specific files and/or extensions.

    -includeresource: {src/main/resources}, {legal=contracts}

## Ignoring Missing Sources

A *source* in the clause starting with a `-` sign will not generare an error when the source in the clause cannot be located. This is very convenient if you specify an global `-includeresource` instruction in `build.bnd`. For example, `-includeresource.all = -src/main/resources` will not complain when a project does not have a `src/main/resources` directory. Note that the minus sign must be on the *source*. E.g.

    `-includeresource.all = {foo=-bar}`, -foo.txt

## Rolling

There are two variants of the rolling *operator* `@`. It can be used to *roll up* a directory as a zip or jar file, or it can be used to unroll a jar file into its constituents.

If the destination is a path of a `jar` or `zip` file, like `foo/bar/icons.zip` and the source points to a directory in the file system, then the directory will be wrapped up in a Jar and stored as a single entry in the receiving jar file.

    -includeresource    foo/bar/icons.zip=@icons/

*Unrolling* is getting the content from another JAR. It is activated by starting the source with an at sign (`@`). The at sign signals that it is not the actual file that should be copied, but the contents of that file should be placed in the destination.

    -includeresource    tmp=@jar/foo.jar

The part that follows the at sign (`@`) is either a file path or a URL. Without any extra parameters it will copy all resources except the ones in the `-donotcopy` list and the `META-INF/MANIFEST`.

    -includeresource    @jar/foo.jar

This is an ideal way to wrap a bundle since it is a full copy. After that one can add additional resources or use `-exportcontents` to export the contained packages in the normal way. In this way, bnd will calculate all imports.

The unrolling can also be restricted with a single *selector*. The syntax for the selector must start with a `!/` marker, which is commonly used for this purpose. After the `!/` the normal selector operators and patterns can be used. For example, if we want to get just the `LICENSE` from a bundle then we can do:

    -includeresource    @jar/foo.jar!/LICENSE

However, since selectors can also negate, it is also possible to do the reverse:

    -includeresource    "@jar/foo.jar!/!LICENSE"

This is a single selector, it is therefore not possible to specify a chain with rejections and selections. However, also a single selector can match multiple file paths:

    -includeresource    @jar/osgi.jar!/!(LICENSE|about.html|org/*)

Wrapping often requires access to a JAR from the repository. It is therefore common to see the unrolling feature being combined with the `${repo}` macro. The `${repo}` macro is given the Bundle Symbolic Name and an optional version range returns the path to a file from the repository.

    -includeresource    @${repo;biz.aQute.bndlib}!/about.html

### Unrolling options

`flatten:=BOOLEAN` - puts all files in the file-tree into one folder

    -includeresource new.package/=@jar/file.jar!/META-INF/services/*;flatten:=true

`rename:=RENAME` - maps the path using a given renaming instruction. Paths are filtered by the given instruction-SELECTOR. The instruction-Selector is compiled to a regex-pattern. This pattern is used to generate a matcher by using the filtered path and the matcher is used to replaceAll using the given extra value.

    -includeresource new.package=@jar/cxf-rt-rs-sse-3.2.5.jar!/(META-INF)/(c*f)/(*);rename:=$2/$1/$3.copy

`onduplicate` - controls duplicate file handling for files with the same path and filename. See **Handling duplicates** below.

### Handling duplicates

When unrolling multiple jar files into your target jar then duplicates can occur when multiple files share the same path and filename. By default duplicates overwrite existing files (last wins).
With the `onduplicate` directive you can control this behavior. For example there is the command `onduplicate:=MERGE` which by default is able to merge (append) services files in `/META-INF/services/`.

**Examples:**

- `onduplicate:=OVERWRITE` - (default) duplicates overwrite existing files (in other words: last wins)
- `onduplicate:=MERGE` - tries to merge duplicate files under `/META-INF/services/` by default. Other paths are skipped.
- `onduplicate:='MERGE,metainfservices'` - same as MERGE. `metainfservices` is a tag which pulls in Plugins with this tag. Currently there is one default Plugin for handling files under `/META-INF/services/`
- `onduplicate:='sometag'` - tries to merge with Plugins tagged with `sometag`
- `onduplicate:=SKIP` - duplicates are skipped (in other words: first wins)
- `onduplicate:=WARN` - output a warning if there are duplicates
- `onduplicate:=ERROR` output an error if there are duplicates

`WARN` and `ERROR` can be combined with other commands, while OVERWRITE, MERGE, SKIP are mutually exclusive.
So combinations are possible, e.g.

- `onduplicate:=WARN,MERGE` - this outputs are warning if duplicates occur, but also tries to merge files under `META-INF/services`.

#### Example - Handling duplicates

Let's take Apache FOP as an example. This library comes with 4 jars which are not OSGi bundles.
To use them in OSGi you could wrap them with bnd. Because of some classloading issues related to the ServiceLoader mechanism, one way to do it is to combine them into a single bundle.
One challenge is its extension mechanism which uses ServiceLoaders under `/META-INF/services/`.

For example the bundle `fop-core` contains a file `META-INF/services/org.apache.xmlgraphics.image.loader.spi.ImagePreloader` with the content:

```
org.apache.fop.image.loader.batik.PreloaderWMF
org.apache.fop.image.loader.batik.PreloaderSVG
```

`xmlgraphics-commons` contains the same file  `META-INF/services/org.apache.xmlgraphics.image.loader.spi.ImagePreloader` with content:

```
org.apache.xmlgraphics.image.loader.impl.PreloaderTIFF
org.apache.xmlgraphics.image.loader.impl.PreloaderGIF
org.apache.xmlgraphics.image.loader.impl.PreloaderJPEG
org.apache.xmlgraphics.image.loader.impl.PreloaderBMP
org.apache.xmlgraphics.image.loader.impl.PreloaderEMF
org.apache.xmlgraphics.image.loader.impl.PreloaderEPS
org.apache.xmlgraphics.image.loader.impl.imageio.PreloaderImageIO
org.apache.xmlgraphics.image.loader.impl.PreloaderRawPNG
```

If you combine these two jars into a single target jar you want to ensure that both files do not overwrite each other but are merged / appended instead, in order to be a valid ServiceLoader file.

This can be achieved by the following instructions:

```
@${repo;org.apache.xmlgraphics:fop-core;latest}!/*,\
@${repo;org.apache.xmlgraphics:xmlgraphics-commons;latest}!/*;onduplicate:=MERGE,\

```

The instructions above can be read like this:

- the first line `fop-core` can be considered the parent which is unrolled without any special handling.
- the second line is the interesting one: The `onduplicate:=MERGE` directive tells bnd to try merging files. By default bnd is only able to merge files under `META-INF/services`. So bnd will append the duplicate file to the existing file with a line break.

## Literals

For testing purposes it is often necessary to have tiny resources in the bundle. These could of course be placed on the file system but bnd can also generate these on the fly. Since these are defined in the bnd files, the content has full access to the macros. This is done by specifying a `literal` attribute on the clause.

    -includeresource    foo.txt;literal='This is some content with a macro ${sum;1,2,3}'

The previous example will create a resource with the given content.

## Flattening & Recurse

When a directory is specified bnd will by default recurse the source and create a similar hierarchy on the destination.

The recursion and the hierarchy can be controlled with directives.

    -includeresource    target/=hierarchy/

The `recursive:` directive can be used to indicate that the source should not be recursively traversed by specifying `false`:

    -includeresource    target/=hierarchy/;recursive:=false

In this case, only the `hierarchy` directory itself will be copied to the `target` directory.  The `flatten:` directive indicates that if the directories are recursively searched, the output must not create any directories. That is all resources are flattened in the output directory.

    -includeresource    target/=hierarchy/;flatten:=true

## Sample usages

### Simple form

| Instruction | Explanation |
| --- | --- |
| `-includeresource: lib/fancylibrary-3.12.0.jar` | Copy lib/fancylibrary-3.12.0.jar file into the root of the target JAR |
| `-includeresource.resources: -src/main/resources` | Copy folder src/main/resources contents (including subdfolders) into root of the target JAR <br>The arbitrarily named suffix .resources prevents this includeresource directive to be overwritten <br>The preceding minus sign instructs to supress an error for non-existing folder src/main/resources |
| `-includeresource: ${workspace}/LICENSE, {readme.md}` | Copy the LICENSE file residing in the bnd workspace folder (above the project directory) as well as the pre-processed readme.md file (allowing for e.g. variable substitution) in the project folder into the target JAR |
| `-includeresource: ${repo;com.acme:foo;latest}` | Copy the com.acme.foo bundle JAR in highest version number found in the bnd workspace repository into the root of the target JAR |

### Assignment form

| Instruction | Explanation |
| --- | --- |
| `-includeresource: images/=img/` or <br>`-includeresource: images=img` | Copy contents of img/ folder (including subdfolders) into an images folder of the target JAR |
| `-includeresource: x=a/c/c.txt` | Copy a/c/c.txt into file x in the root folder of the target JAR |
| `-includeresource: x/=a/c/c.txt` | Copy a/c/c.txt into file x/c.txt in the root folder of the target JAR |
| `-includeresource: libraries/fancylibrary.jar=lib/fancylibrary-3.12.jar; lib:=true` | Copy lib/fancylibrary-3.1.2.jar from project into libraries folder of the target JAR, and place it on the Bundle-Classpath (BCP). It will make sure the BCP starts with '.' and then each include resource that is included will be added to the BCP |
| `-includeresource: lib/; lib:=true` | Copy every JAR file underneath lib in a relative position under the root folder of the target JAR, and add each library to the bundle classpath |
| `-includeresource: acme-foo-snap.jar=${repo;com.acme:foo;snapshot}` | Copy the highest snapshot version of com.acme.foo found in the bnd workspace repository as acme-foo-snap.jar into the root of the target JAR |
| `-includeresource: foo.txt;literal='foo bar'` | Create a file named foo.txt containing the string literal "foo bar" in the root folder of the target JAR |
| `-includeresource: bsn.txt;literal='${bsn}'` | Create a file named bsn.txt containing the bundle symbolic name (bsn) of this project in the root folder of the target JAR |
| `-includeresource: libraries/=lib/;filter:=fancylibrary-*.jar;recursive:=false;lib:=true` or <br>`-includeresource: libraries/=lib/fancylibrary-*.jar;lib:=true` (as of bndtools 4.2) | Copy a wildcarded library from lib/ into libraries and add it to the bundle classpath |

### Inline form

| Instruction | Explanation |
| --- | --- |
| `-includeresource: @lib/fancylibrary-3.12.jar!/*` | Extract the contents of lib/fancylibrary-3.12.jar into the root folder of the target JAR, preserving relative paths |
| `-includeresource: @${repo;com.acme.foo;latest}!/!META-INF/*` | Extract the contents of the highest found com.acme.foo version in the bnd workspace repository into the root folder of the target JAR, preserving relative paths, excluding the META-INF/ folder |
