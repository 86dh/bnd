---
layout: default
title: -jpms-module-info-options module-infos+
class: JPMS
summary: |
   Used to generate the `module-info.class`
note: AUTO-GENERATED FILE - DO NOT EDIT. You can add manual content via same filename in ext folder. 
---

- Example: `-jpms-module-info-options: java.enterprise;transitive="true"`

- Pattern: `.*`

<!-- Manual content from: ext/jpms_module_info_options.md --><br /><br />
See [jpms](../chapters/330-jpms.html) for an overview and the detailed rules how the `module-info.class` file is
calculated. 

The `-jpms-module-info-options` instruction provides some capabilities to help the developer handle these scenarios. The keys of these instructions are module names and there are 4 available attributes. 

    -jpms-module-info-options       ::= moduleinfo
    moduleinfo                      ::= NAME 
                                        [ ';substitute=' substitute ] 
                                        [ ';ignore=' ignore ] 
                                        [ ';static=' static ] 
                                        [ ';transitive=' transitive ]
    
They attributes are:


- **`substitute`** - If bnd generates a module name based on the file name and it matches the value of this attribute it should be substituted with the key of the instruction.
  e.g. 
  
    -jpms-module-info-options: java.enterprise;substitute="geronimo-jcdi_2.0_spec"

  means that if bnd calculates the module name to be `geronimo-jcdi_2.0_spec` it should replace it with `java.enterprise` 
  
– **`ignore`** - If the attribute `ignore="true"` is found the require matching the key of the instruction will not be added.
  e.g. 
  
  ```properties
  -jpms-module-info-options: java.enterprise;ignore="true"
  ```

  means ignore the module `java.enterprise`
  
- **`static`** - If the attribute `static="true|false"` is found the access of the module matching the key of the instruction will be set to match.
  e.g. 
  
  ```properties
  -jpms-module-info-options: java.enterprise;static="true"
  ```

  means make the `require` for module `java.enterprise` `static`
  
- **`transitive`** - If the attribute `transitive="true|false"` is found the access of the module matching the key of the instruction will be set to match.
  e.g. 
  
  ```properties
  -jpms-module-info-options: java.enterprise;transitive="true"
  ```
  
  means make the `require` for module `java.enterprise` `transitive`

The following is an example with multiple attributes and instructions:

```properties
-jpms-module-info-options: \
    java.enterprise;substitute="geronimo-jcdi_2.0_spec";static=true;transitive=true,\
    java.management;ignore=true;
```
