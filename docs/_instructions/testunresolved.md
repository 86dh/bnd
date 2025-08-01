---
layout: default
title: -testunresolved BOOLEAN
class: Project
summary: |
   Will execute a JUnit testcase ahead of any other test case that will abort if there are any unresolved bundles.
note: AUTO-GENERATED FILE - DO NOT EDIT. You can add manual content via same filename in ext folder. 
---

- Example: `-testunresolved=true`

- Values: `true,false`

- Pattern: `true|false|TRUE|FALSE`

<!-- Manual content from: ext/testunresolved.md --><br /><br />

The `-testunresolved` instruction controls whether a special JUnit test case is executed before any other test cases to check for unresolved bundles. If any bundles are unresolved, this test will abort the test run and report the issue as a failure.

This is useful for ensuring that your test environment is fully resolved before running tests, helping to catch dependency or configuration problems early in the process.
