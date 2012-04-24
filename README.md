Rundeck WinRM Plugin
--------------------

This is a [Rundeck Node Execution plugin][1] that uses WinRM to connect to Windows and execute commands.  It uses the [OverThere Library][2] to provide the WinRM implementation, and uses Basic authentication over HTTPS.

[1]: http://rundeck.org/docs/manual/plugins.html#node-execution-plugins
[2]: https://github.com/xebialabs/overthere/

Install
====

Copy the `rundeck-winrm-plugin-1.0.jar` to the `libext/` directory for Rundeck.

Configure The Plugin
====

This plugin provides a NodeExecutor called `overthere-winrm`, which you can set as on your node defintion:

	<node name="winNode" node-executor="overthere-winrm" .../>

Or set as the default NodeExecutor for your project/framework properties file, with `service.NodeExecutor.default.provider=overthere-winrm`.

These Node attributes are used to connect to the remote host:

* `username` - Remote username
* `hostname` - Remote host. Can include "host:port" to specify port number other than the default 5986.
* `winrm-password-option` - Specifies a [Secure Authentication Option][1] from a Job to use as the authentication password. (format: "option.NAME"). 
	* default-value: "option.winrmPassword", so simply define a Secure Authentication Option on your Job with the name "winrmPassword".

[1]: http://rundeck.org/docs/manual/job-options.html#secure-options

These additional configuration attributes can be set on the Node, or in the project.properties or framework.properties. To add them to project.properties, prefix them with "project." and for framework.properties prefix them with "framework.":

* `winrm-timeout` - timeout in milliseconds for connection. (default 15000)
* `winrm-port` - port number to use, if not set in the `hostname` of the Node. (Default: 5986)
* `winrm-user` - Username, if not set in the `username` of the Node
* `winrm-password-option`

Configure a Windows Server for WinRM 
====

This is a seperate topic, but you can follow the guide described in the OverThere project for [WINRM_HTTPS Host Setup][1].

[1]: https://github.com/xebialabs/overthere/#cifs_host_setup

Here are also some notes on the wiki: [wikilink]

Caveats
====

Note: This plugin will work against Rundeck 1.4.3 or later.  A conflict with the "commons-codec" jar dependency prevents it from working in earlier versions out of the box.  If you want you can resolve the issue manually by removing all "commons-codec-1.x.jar" files in your Rundeck server install, and replacing them with "commons-codec-1.5.jar".

Build
=====

Build with gradle or maven 2.

Prerequisites: the `rundeck-core-1.4.x.jar` file.

Gradle build, result is `build/libs/rundeck-winrm-plugin-1.0.jar`.

	gradle clean build

Maven build, result is `target/rundeck-winrm-plugin-1.0.jar`
	
	mvn clean package