---
layout: default
title: -generate srcs ';output=' DIR ( ';' ( system | generate | classpath))* ...
class: Project
summary: |
   Generate sources
note: AUTO-GENERATED FILE - DO NOT EDIT. You can add manual content via same filename in ext folder. 
---

- Example: `-generate:   \
        gen/**.java; \ 
            output='src-gen/' ; \ 
            generate='javagen -o src-gen gen/'`

- Pattern: `.*`

<!-- Manual content from: ext/generate.md --><br /><br />

Virtually all the work bnd is concerned about happens in generating the JAR file. The key idea is to _pull_ resources in the JAR, instead of the more traditional _push_ model of other builders. This works well, except for _generating source code_. This generating step must happen before the compiler is called, and the compiler is generally called before bnd becomes active. 

This `-generate` instruction specifies the code generating steps that must be executed. Source code can be generated by _system_ commands or the bnd _external plugins_.

	-generate	::= 	clause ( ',' clause )*
	clause		::= 	FILESET ';' 'output=' DIR (';' option )*
	src		::=	FILESET
	option		::= 	'system=' STRING
			|   'generate=' STRING
	        |   'classpath=' PATH
	        |   'workingdir=' FILE
	        |   'clear=' BOOLEAN
	        |   'version=' RANGE

For each clause, the key of the clause is used to establish an Ant File Set, e.g. `foo/**.in`. This a glob expression with the exception that the double wildcard ('**') indicates to any depth of directories. The `output` attribute _must_ specify a directory. If the output must be compiled this directory must be on the bnd source path.

The output directory will created if it does not exist. It will be cleared of any previous generate results before a run, except if the option `clear` is set to false. In this case the used Generator needs to deal with the remnants in the directory itself.

If any file in the source is older than any file in the target (to any depth), or the target is empty, the clause is considered _stale_. If the clause is not stale, it is further ignored. If no further options are set on the clause, a warning is generated that some files are out of date. 

If either a warning or error option is given, these will be executed on the project.

If a command `STRING` is given it is executed as in the `${system}` macro. If the command `STRING` starts with a 
minus sign (`-`) then a failure is not an error, it is reported as warning.

The generate option will execute an _external plugin_ or plain JAR with a `Main-Class` manifest header. The choice is made by looking at the first word in the `generate` attribute.

If this _name_ has a dot in it, like in a fully qualified class name, it is assume that species a _class name_. (If the name starts with a dot, it will be assume to be a name in the default package.) In this case, the `classpath` attribute of the instruction can be used to provide additional JARs on the command's classpath. The format of PATH is the standard format for instructions like -buildpath. In this case, you can also set the `workingdir` to a directory. This directory is specified relative to the project.

Without a dot in the name, the name is assumes to be an _external plugin_ name, with the `objectClass` (service type) of `Generator<? extends Options>`. External , or Main-Class jars, can come from an external repository or a local workspace project.

The `generate` value is a _command line_. It can use the standard _unix_ like way of specifying a command. It supports flags (boolean parameters) and parameter that take a value. When this external plugin is executed, it is expected to create files fall within the _target_, if not, an error is reported. These changed or created files are refreshed in Eclipse.

The command line can be broken in different commands with the semicolon (`';'`), like a unix shell. Redirection of stdin (`'<'`), stdout (`'>'`, or `'1>'`), and stderr (`'2>'`) are supported.  The path for redirection is relative to the project directory, even if `workingdir` has been specified.

With the `version` version range attribute it is possible to restrict the candidates if there are multiple versions available. The code will select the highest version if only one is used.

## Example with an External Plugin

Include in the bnd build is a _javagen_ external plugin that is useful to generate Java code based on build information. It uses a template directory with Java files. When the external plugin runs, it will use all these files to write matching Java files in the output directory, in a matching package directory. The input Java files can be prefixed with a properties header:

    ---
    foo: 1
    ---
    package foo.bar;
    class Foo {
        int foo = ${foo};
    }

The optional header is removed and then the remainder of the file is ran through the bnd macro processor.

If this example is used, it is necessary to add a new _source folder_. In Eclipse, this requires adding an entry in the `.classpath` file, in bnd it requires the modification of the `src` property.  

    src=${^src},src-gen

Assuming that the input files are in the `gen` directory, the following can be used to automatically generate the output files based on the input files.

    -generate:   \
        gen/**.java; \ 
            output='src-gen/' ; \ 
            generate='javagen -o src-gen gen/'

## Example with a Main Class JAR

JFlex and CUP are popular tools to create _lexers_ and _parsers_. The JARs are on Maven Central with  Main-Class manifest attribute. The JFlex JAR, however, requires the `cup_runtime` JAR on the classpath.

We can directly use these executable JARs with the `-generate` instruction.

    -generate: \
        lex/Foo.lex; \
            output      = gen-src/ \
            generate    = `jflex.Main -d lex-gen/ lex/Foo.lex'; \
            classpath   = 'de.jflex:cup_runtime;version=11b-20160615'

Notice that we use the `maven` GAV format here to find the `cup_runtime` because these JARs are unfortunately not bundles.


## Writing a Command

It is possible to create an External Plugin or a Main Class command your in the same workspace as where you apply the command. This makes it easy to develop commands interactively. The easiest way is to make an External Plugin. The support for Main-Class is mostly to support existing JAR.

The following is an Generate external plugin that outputs "hello":

    @ExternalPlugin(name = "hello", objectClass = Generator.class)
    public class Hello implements Generator<HelloOptions> {
    
        public interface HelloOptions extends Options {
            boolean upper();
            String name(String defaultName)
        }
        
        public Optional<String> generate(
            BuildContext context, 
            HelloOptions options) throws Exception {
            String hello = "Hello " + options.name("World");
            if ( options.upper() )
                hello = hello.toUpperCase();
                
            System.out.println( hello );
            return Optional.empty();
        }
    }

The ` @ExternalPlugin` annotation creates an external plugin _capability_ in the bundle's manifest. The name is the name we can use as the command name. Do not use a name that has dots in it, this will then be confused with a main class command.

The Hello class implements `Generator<HelloOptions>`. This is the type the generate code expects. 

The type parameter specifies a _specification interface_. This interface is used to specify the command line. A `boolean` method is a _flag_, and any other type is an _option_. The first character of the method is the name of the flag or option. For example, `boolean upper()` is a flag and has the `-u` and `--upper`. Flags can be combined. For example, if you have a flag `-a` and `-b` then you can also use `-ab`.

Options take parameters. For example, a `String name()` option would be set as `-n World`. The method on the spec interface can optionally take an argument, this argument is used as default if the option is not used in the command line. The argument type and the return type can be different. For example, if the return type is `File`, then the parameter type can be `String` so that the returned `File` is resolved against the base directory of the build.

Options that return `File` will resolve the input against the project directory. A Set<File> will accept an ant file set specification like `gen/**.java`.

The Options interface specifies a number of additional keys:

* _arguments – A list with the arguments, flags and options removed
* _properties – The properties that contains the flags and options

The actual code is in the `generate(BuildContext,HelloOptions)` method. the Build Context contains useful context information. 

It is ok to write to the System.out and System.err. The output is captured and can be redirected in the command line in the standard Unix way. 

The code is expected to return an `Optional.empty()` when everything is ok. If something is wrong, an actualString can be used to explain the error. Return a non-empty fails the call and the error will be reported.

If you the plugin source code is in the same workspace as the project using this plugin, you must make sure that the external plugin project is build before the project that uses it. You can achieve this with [-dependson](dependson.html). 

You can take a look at the [JavaGen](https://github.com/bndtools/bnd/tree/master/biz.aQute.bnd.javagen) project in the bnd build to see how an actual external plugin is made.
