---
layout: default
title: -remoteworkspace (true|false)
class: Project
summary: |
   Enable the workspace to server remote requests from the local system, needed for Launchpad
note: AUTO-GENERATED FILE - DO NOT EDIT. You can add manual content via same filename in ext folder. 
---

- Example: `-remoteworkspace: true`

- Values: `true,false`

- Pattern: `true|false|TRUE|FALSE`

<!-- Manual content from: ext/remoteworkspace.md --><br /><br />

Launchpad is a library that enables testing in local JUnit settings. Launchpad needs access to the enclosing 
bnd workspace. However, this workspace runs in another process then the test code. Launchpad will therefore
attempt to a _workspace remote server_. 

For security reasons, this remote workspace server is not enabled by default. It requires:

    -remoteworkspace        true

Remote Workspace servers can be nested. That is, you can run Eclipse and then Gradle on the same workspace.
Launchpad will use the latest initialized remote workspace.

## Details

If you enable the remote workspace, its socket's port will be registered in the `/cnf/cache/remotews` directory.

The remote workspace server can only be accessed from the local machine on 127.0.0.1 to prevent outside 
attacks.
