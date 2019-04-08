# dslink-java-v2-example

* Java - version 1.8 and up.
* [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0)


## Overview

This is a simple link that can be copied and used as boilerplate for a new Java DSLink.

If you are not familiar with DSA and links, an overview can be found at
[here](http://iot-dsa.org/get-started/how-dsa-works).

This link was built using the DSLink Java SDK which can be found
[here](https://github.com/iot-dsa-v2/sdk-dslink-java-v2).


## Creating a New Link

When creating a new link from this source material, you should do the following:

1. Edit the code
    - Change the package and directories to match your organization.
2. Modify build.gradle
    - group - Your organization's identifier.
    - You will probably need to change your dependencies.
3. Modify LICENSE
    - At the very least, change the copyright holder.
4. Modify dslink.json
    - Change name, version, and description.
    - For DSA v1, set dsa-version to "1.0".  Use "2.0" for DSA v2.
    - Change main, this is the path to the shell script used to launch the link.  It is 
      created by the distZip task.
    - Change the value of the config named "main-node". This must be the fully qualified
      class name of your main node.
5. Edit this README
    - Please maintain a helpful readme.
    - Change the title.
    - Maintain the current version number.
    - Change the license if necessary.
    - Provide an overview of the link's purpose.  Keep the text linking to the DSA overview
      and core SDK for context.
    - Remove this section (Creating a New Link).
    - Update the Link Architecture to match your node hierarchy.
    - Update the Node Guide accordingly.
    - Acknowledge any 3rd party libraries you use.
    - Maintain a version history.

## Link Architecture

This section outlines the hierarchy of nodes defined by this link.

- _MainNode_ - The root node of the link.
  - _ExampleChild_ - There is no child, this is just a documentation example.


## MainNode

This is the root node of the link.  It has a counter that is updated on a short interval,
only when the node is subscribed.  It also has a simple action to reset the counter.

_Actions_
- Reset - Resets the counter to 0.

_Values_
- Counter - Automatically updates whenever the node is subscribed.

_Child Nodes_
- There are no child nodes.


## Acknowledgements

SDK-DSLINK-JAVA

This software contains unmodified binary redistributions of 
[sdk-dslink-java-v2](https://github.com/iot-dsa-v2/sdk-dslink-java-v2), which is licensed 
and available under the Apache License 2.0. An original copy of the license agreement can be found 
at https://github.com/iot-dsa-v2/sdk-dslink-java-v2/blob/master/LICENSE

