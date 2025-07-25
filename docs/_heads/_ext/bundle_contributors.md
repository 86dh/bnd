---
layout: default
class: Header
title: Bundle-Contributors ... 
summary: Lists the bundle contributors according to the Maven bundle-contributors pom entry
---

# Bundle-Contributors

The `Bundle-Contributors` header lists contributors to the bundle, as defined in the Maven POM or via annotations. This header is not standardized by OSGi but is used for documentation and tracking purposes.

Example:

```
Bundle-Contributors: John Doe;roles='developer';organization='Example Corp.'
```

This header is optional and is mainly used for informational purposes.
	
	/*
	 * Bundle-Contributors header
	 */

	private void doBundleContributors(BundleContributors annotation) throws IOException {
		StringBuilder sb = new StringBuilder(annotation.value());
		if (annotation.name() != null) {
			sb.append(";name='");
			escape(sb, annotation.name());
			sb.append("'");
		}
		if (annotation.roles() != null) {
			sb.append(";roles='");
			escape(sb,annotation.roles());
			sb.append("'");
		}
		if (annotation.organizationUrl() != null) {
			sb.append(";organizationUrl='");
			escape(sb,annotation.organizationUrl());
			sb.append("'");
		}
		if (annotation.organization() != null) {
			sb.append(";organization='");
			escape(sb,annotation.organization());
			sb.append("'");
		}
		if (annotation.timezone() != 0)
			sb.append(";timezone=").append(annotation.timezone());
		add(Constants.BUNDLE_CONTRIBUTORS, sb.toString());
	}
	
	
			/**
		 * Maven defines contributors and developers in the POM. This annotation will
		 * generate a (not standardized by OSGi) Bundle-Contributors header.
		 * <p>
		 * This annotation can be used directly on a type or it can 'color' an
		 * annotation. This coloring allows custom annotations that define a specific
		 * contributor. For example:
		 * 
		 * <pre>
		 *   {@code @}BundleContributor("Peter.Kriens@aQute.biz")
		 *   {@code @}interface pkriens {}
		 *   
		 *   {@code @}pkriens
		 *   public class MyFoo {
		 *     ...
		 *   }
		 * </pre>
		 * 
		 * Duplicates are removed before the header is generated and the coloring does
		 * not create an entry in the header, only an annotation on an actual type is
		 * counted. This makes it possible to make a library of contributors without
		 * then adding them all to the header.
		 * <p>
		 * See <a href=https://maven.apache.org/pom.html#Developers>Maven POM reference</a>
		 */
		@Retention(RetentionPolicy.CLASS)
		@Target({
				ElementType.ANNOTATION_TYPE, ElementType.TYPE
		})
		public @interface BundleContributors {
		
			/**
			 * The email address of the developer.
			 */
			String value();
		
			/**
			 * The display name of the developer. If not specified, the {@link #value()}
			 * is used.
			 */
			String name() default "";
		
			/**
			 * The roles this contributor plays in the development.
			 */
			String[] roles() default {};
		
			/**
			 * The name of the organization where the contributor works for.
			 */
			String organization() default "";
		
			/**
			 * The url of the organization where the contributor works for.
			 */
			String organizationUrl() default "";
		
			/**
			 * Time offset in hours from UTC without Daylight savings
			 */
			int timezone() default 0;
		}

<hr />
TODO Needs review - AI Generated content		