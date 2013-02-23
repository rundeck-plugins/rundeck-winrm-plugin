Rundeck WinRM Plugin
--------------------

This is a [Rundeck Node Execution plugin][1] that uses WinRM to connect to Windows and execute commands.  It uses the [OverThere Library][2] to provide the WinRM implementation, and uses Basic authentication over HTTPS.

[1]: http://rundeck.org/docs/manual/plugins.html#node-execution-plugins
[2]: https://github.com/xebialabs/overthere/

Install
====

Copy the `rundeck-winrm-plugin-1.0-beta.jar` to the `libext/` directory for Rundeck.

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
* `winrm-protocol` - Determine HTTP(S) protocol to use, either "http" or "https". Default: "https"
* `winrm-auth-type` - Type of authentication to use, "basic" or "kerberos", default: "kerberos"

Using Kerberos Authentication
====

Kerberos authentication can be used with the OverThere 2.0.0-SNAPSHOT library.

Configure these node properties, or set "framework.X" or "project.X" in your framework.properties or project.properties.

* `winrm-cert-trust` - (HTTPS only) certificate trust strategy, "all" (trust all certificates), "self-signed" (trust self-signed in addition to verified), or "default" (trust only verified certificates). Default: "default".
* `winrm-hostname-trust` - (HTTPS only) hostname trust strategy, "all", "strict" or "browser-compatible". Default: "browser-compatible".
* `winrm-kerberos-debug` - true/false, if true, enable debug output for Kerberos authentication. Default: false.

Configure Kerberos
----

Kerberos authentication requires you to set up some Java System Properties, or a kb5.conf file to define which domains map to which Domain Controllers.  You can follow the [Kerberos Requirements](http://docs.oracle.com/javase/1.4.2/docs/guide/security/jgss/tutorials/KerberosReq.html) for Java.  To simply use a single domain and KDC, set these Java System Props at startup of the Rundeck server:

    -Djava.security.krb5.realm=<example.com> -Djava.security.krb5.kdc=<kdc.example.com>. 

Replace the values with the name of your domain/realm and the hostname of your domain controller.

FAQ
----

If you do not do follow the [Configure Kerberos](#configure-kerberos) section this you will see this error:

    failed: WinRM Error: javax.security.auth.login.LoginException: Could not load configuration from SCDynamicStore

If your KDC is not reachable you will see this error:

    failed: WinRM Error: javax.security.auth.login.LoginException: Receive timed out

If your password is incorrect you will see this error:

    failed: WinRM Error: javax.security.auth.login.LoginException: Pre-authentication information was invalid (24)

If your username is not found you will see this error:

    failed: WinRM Error: javax.security.auth.login.LoginException: Client not found in Kerberos database (6)

If the system clock differs too much between the nodes you will see this error:

    failed: WinRM Error: javax.security.auth.login.LoginException: Clock skew too great (37)

If you receive Kerberos authentication error `Server not found in Kerberos database` then you need to define a "Service Principal Name" for the auth service on the Windows node:

* Run this command, include the port number used (e.g. 5986 for HTTPS, 5985 for HTTP) , but make sure the service name starts with "HTTP/":

        setspn -a HTTP/hostname.domain.com:5985 hostname
    or
        setspn -a HTTP/hostname.domain.com:5986 hostname


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

Gradle build, result is `build/libs/rundeck-winrm-plugin-1.0-beta.jar`.

	gradle clean build

Maven build, result is `target/rundeck-winrm-plugin-1.0-beta.jar`
	
	mvn clean package
