---
layout: default
title: Conditional-Package PACKAGE-SPEC ( ',' PACKAGE-SPEC ) *
class: Header
summary: |
   Recursively add packages from the class path when referred and when they match one of the package specifications.
note: AUTO-GENERATED FILE - DO NOT EDIT. You can add manual content via same filename in ext folder. 
---

- Example: `Conditional-Package: com.*`

- Values: `${packages}`

- Pattern: `.*`

<!-- Manual content from: ext/conditional_package.md --><br /><br />

# Conditional-Package

This instruction is equal to using [-conditionalpackage](conditionalpackage.html) except for the fact that the header in addition will be copied into the generated bundle manifest (like all headers beginning with a capital letter).

The `Conditional-Package` header allows you to specify package patterns that, when referred to by your code, will be included in the bundle if they match the given specifications. This is useful for conditionally including packages from the classpath based on actual usage.

Example:

```
Conditional-Package: com.example.optional.*
```

bnd will recursively add matching packages until no more additions are found. This header is useful for advanced packaging scenarios.

	/**
	 * Answer extra packages. In this case we implement conditional package. Any
	 */
	@Override
	protected Jar getExtra() throws Exception {
		Parameters conditionals = getParameters(CONDITIONAL_PACKAGE);
		conditionals.putAll(getParameters(CONDITIONALPACKAGE));
		if (conditionals.isEmpty())
			return null;
		trace("do Conditional Package %s", conditionals);
		Instructions instructions = new Instructions(conditionals);

		Collection<PackageRef> referred = instructions.select(getReferred().keySet(), false);
		referred.removeAll(getContained().keySet());

		Jar jar = new Jar("conditional-import");
		addClose(jar);
		for (PackageRef pref : referred) {
			for (Jar cpe : getClasspath()) {
				Map<String,Resource> map = cpe.getDirectories().get(pref.getPath());
				if (map != null) {
					copy(jar, cpe, pref.getPath(), false);
					// Now use copy so that bnd.info is processed, next line
					// should be
					// removed in the future TODO
					// jar.addDirectory(map, false);
					break;
				}
			}
		}
		if (jar.getDirectories().size() == 0) {
			trace("extra dirs %s", jar.getDirectories());
			return null;
		}
		return jar;
	}



<hr />
TODO Needs review - AI Generated content
