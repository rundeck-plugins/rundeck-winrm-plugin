package com.dtolabs.rundeck.plugin.overthere;


import static com.xebialabs.overthere.ConnectionOptions.*;
import static com.xebialabs.overthere.OperatingSystemFamily.WINDOWS;
import static com.xebialabs.overthere.cifs.CifsConnectionBuilder.CONNECTION_TYPE;
import static com.xebialabs.overthere.cifs.CifsConnectionType.*;
import static com.xebialabs.overthere.util.ConsoleOverthereProcessOutputHandler.consoleHandler;

import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.Describable;
import com.dtolabs.rundeck.core.plugins.configuration.Description;
import com.dtolabs.rundeck.core.plugins.configuration.Property;
import com.xebialabs.overthere.CmdLine;
import com.xebialabs.overthere.ConnectionOptions;
import com.xebialabs.overthere.OverthereConnection;

import com.dtolabs.rundeck.core.execution.service.NodeExecutor;
import com.dtolabs.rundeck.core.execution.service.NodeExecutorResult;
import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.common.FrameworkProject;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.dispatcher.DataContextUtils;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.ExecutionException;
import com.xebialabs.overthere.cifs.CifsConnectionBuilder;
import com.xebialabs.overthere.cifs.CifsConnectionType;
import com.xebialabs.overthere.cifs.winrm.exception.WinRMRuntimeIOException;
import com.xebialabs.overthere.util.DefaultAddressPortMapper;

import java.util.*;

@Plugin (name = OTWinRMNodeExecutor.SERVICE_PROVIDER_TYPE, service = "NodeExecutor")
public class OTWinRMNodeExecutor implements NodeExecutor, Describable {
    public static final String SERVICE_PROVIDER_TYPE = "overthere-winrm";

    public static final int DEFAULT_WINRM_CONNECTION_TIMEOUT = 15000;
    public static final String WINRM_PASSWORD_OPTION = "winrm-password-option";
    public static final String DEFAULT_WINRM_PASSWORD_OPTION = "option.winrmPassword";
    public static final int DEFAULT_WINRM_PORT = 5986;
    public static final String WINRM_TIMEOUT_PROPERTY = "winrm-timeout";
    public static final String WINRM_USER = "winrm-user";
    public static final String WINRM_PORT = "winrm-port";
    public static final String WINRM_AUTH_TYPE = "winrm-auth-type";
    public static final String WINRM_CERT_TRUST = "winrm-cert-trust";
    public static final String WINRM_HOSTNAME_TRUST = "winrm-hostname-trust";
    public static final String WINRM_PROTOCOL = "winrm-protocol";
    public static final String KERBEROS_AUTH = "kerberos";
    public static final String BASIC_AUTH = "basic";
    public static final String DEBUG_KERBEROS_AUTH = "debugKerberos";
    public static final Boolean DEBUG_KERBEROS_AUTH_DEFAULT = false;
    public static final String DEFAULT_WINRM_PROTOCOL = "https";

    private Framework framework;
    private static final String PROJ_PROP_PREFIX = "project.";
    private static final String FWK_PROP_PREFIX = "framework.";
    private static final String PROJ_PROP_WINRM_TIMEOUT = PROJ_PROP_PREFIX + WINRM_TIMEOUT_PROPERTY;
    private static final String CONFIG_TIMEOUT = "timeout";

    public OTWinRMNodeExecutor(final Framework framework) {
        this.framework = framework;
    }

    static final List<Property> CONFIG_PROPERTIES = new ArrayList<Property>();
    static final Map<String, String> CONFIG_MAPPING;


    static {
        //TODO: add config options for SSL certificate checking
        final Map<String, String> mapping = new HashMap<String, String>();
        CONFIG_MAPPING = Collections.unmodifiableMap(mapping);
    }

    static final Description DESC = new Description() {
        public String getName() {
            return SERVICE_PROVIDER_TYPE;
        }

        public String getTitle() {
            return "WinRM";
        }

        public String getDescription() {
            return "Executes a command on a remote windows node via WinRM.";
        }

        public List<Property> getProperties() {
            return null;
        }

        public Map<String, String> getPropertiesMapping() {
            return null;
        }
    };

    public Description getDescription() {
        return DESC;
    }


    public NodeExecutorResult executeCommand(final ExecutionContext context, final String[] command,
                                             final INodeEntry node) throws
        ExecutionException {
        if (null == node.getHostname() || null == node.extractHostname()) {
            throw new ExecutionException(
                "Hostname must be set to connect to remote node '" + node.getNodename() + "'");
        }
        if (null == node.extractUserName()) {
            throw new ExecutionException(
                "Username must be set to connect to remote node '" + node.getNodename() + "'");
        }

        final ConnectionOptionsBuilder connectionOptionsBuilder = new ConnectionOptionsBuilder(context, node,
            framework);

        int result = -1;
        String message = null;
        try {

            final OverthereConnection connection = new CifsConnectionBuilder(CifsConnectionBuilder.CIFS_PROTOCOL,
                connectionOptionsBuilder.build(),
                new DefaultAddressPortMapper()).connect();

            try {
                result = connection.execute(consoleHandler(), CmdLine.build(command));
            } finally {
                connection.close();
            }
        } catch (WinRMRuntimeIOException re) {
            if (context.getLoglevel() > 2) {
                re.printStackTrace(System.err);
                message = re.getMessage();
            } else {
                message = "WinRM Error: " + re.getCause();
            }
            context.getExecutionListener().log(0,
                "[" + SERVICE_PROVIDER_TYPE + "] failed: " + message);
        } catch (RuntimeException re) {
            if (context.getLoglevel() > 2) {
                re.printStackTrace(System.err);
                message = re.getMessage();
            } else {
                message = "runtime exception: " + re;
            }
            context.getExecutionListener().log(0,
                "[" + SERVICE_PROVIDER_TYPE + "] failed: " + message);
        } catch (Exception e) {
            if (context.getLoglevel() > 2) {
                e.printStackTrace(System.err);
                message = e.getMessage();
            } else {
                message = "Exception: " + e;
            }
            context.getExecutionListener().log(0,
                "[" + SERVICE_PROVIDER_TYPE + "] failed: " + message);
        }


        final int resultCode = result;
        final boolean status = resultCode == 0;
        final String resultmsg = message;
        if (!status) {
            context.getExecutionListener().log(0,
                "[" + SERVICE_PROVIDER_TYPE + "] failed: " + resultmsg);
        }
        return new NodeExecutorResult() {
            public int getResultCode() {
                return resultCode;
            }

            public boolean isSuccess() {
                return status;
            }

            @Override
            public String toString() {
                return "[" + SERVICE_PROVIDER_TYPE + "] result was " + (isSuccess() ? "success" : "failure")
                       + ", resultcode: "
                       + getResultCode() + (null != resultmsg ? ": " + resultmsg : "");

            }
        };
    }


    static String evaluateSecureOption(final String optionName, final ExecutionContext context) {
        if (null == optionName) {
            return null;
        }
        if (null == context.getPrivateDataContext()) {
            return null;
        }
        final String[] opts = optionName.split("\\.", 2);
        if (null != opts && 2 == opts.length) {
            final Map<String, String> option = context.getPrivateDataContext().get(opts[0]);
            if (null != option) {
                return option.get(opts[1]);
            }
        }
        return null;
    }

    /**
     * Resolve a node/project/framework property by first checking node attributes named X, then project properties
     * named "project.X", then framework properties named "framework.X". If none of those exist, return the default
     * value
     */
    private static String resolveProperty(final String nodeAttribute, final String defaultValue, final INodeEntry node,
                                          final FrameworkProject frameworkProject, final Framework framework) {

        if (null != node.getAttributes().get(nodeAttribute)) {
            return node.getAttributes().get(nodeAttribute);
        } else if (frameworkProject.hasProperty(PROJ_PROP_PREFIX + nodeAttribute)
                   && !"".equals(frameworkProject.getProperty(PROJ_PROP_PREFIX + nodeAttribute))) {
            return frameworkProject.getProperty(PROJ_PROP_PREFIX + nodeAttribute);
        } else if (framework.hasProperty(FWK_PROP_PREFIX + nodeAttribute)) {
            return framework.getProperty(FWK_PROP_PREFIX + nodeAttribute);
        } else {
            return defaultValue;
        }
    }

    private static int resolveIntProperty(final String attribute, final int defaultValue, final INodeEntry iNodeEntry,
                                          final FrameworkProject frameworkProject, final Framework framework) {
        int value = defaultValue;
        final String string = resolveProperty(attribute, null, iNodeEntry, frameworkProject, framework);
        if (null != string) {
            try {
                value = Integer.parseInt(string);
            } catch (NumberFormatException e) {
                throw new ConfigurationException("Not a valid integer: " + attribute + ": " + string);
            }
        }
        return value;
    }

    private static long resolveLongProperty(final String attribute, final long defaultValue,
                                            final INodeEntry iNodeEntry,
                                            final FrameworkProject frameworkProject, final Framework framework) {
        long value = defaultValue;
        final String string = resolveProperty(attribute, null, iNodeEntry, frameworkProject, framework);
        if (null != string) {
            try {
                value = Long.parseLong(string);
            } catch (NumberFormatException e) {
                throw new ConfigurationException("Not a valid long: " + attribute + ": " + string);
            }
        }
        return value;
    }

    private static boolean resolveBooleanProperty(final String attribute, final boolean defaultValue,
                                                  final INodeEntry iNodeEntry,
                                                  final FrameworkProject frameworkProject, final Framework framework) {
        boolean value = defaultValue;
        final String string = resolveProperty(attribute, null, iNodeEntry, frameworkProject, framework);
        if (null != string) {
            value = Boolean.parseBoolean(string);
        }
        return value;
    }

    static class ConfigurationException extends RuntimeException {
        ConfigurationException(String s) {
            super(s);
        }

        ConfigurationException(String s, Throwable throwable) {
            super(s, throwable);
        }
    }

    static class ConnectionOptionsBuilder {

        private ExecutionContext context;
        private INodeEntry node;
        private Framework framework;
        private FrameworkProject frameworkProject;

        ConnectionOptionsBuilder(final ExecutionContext context, final INodeEntry node, final Framework framework) {
            this.context = context;
            this.node = node;
            this.framework = framework;
            this.frameworkProject = framework.getFrameworkProjectMgr().getFrameworkProject(
                context.getFrameworkProject());
        }

        public String getPassword() {
            final String passwordOption = resolveProperty(WINRM_PASSWORD_OPTION, DEFAULT_WINRM_PASSWORD_OPTION, node,
                frameworkProject, framework);
            return evaluateSecureOption(passwordOption, context);
        }

        public int getConnectionTimeout() {
            return resolveIntProperty(WINRM_TIMEOUT_PROPERTY, DEFAULT_WINRM_CONNECTION_TIMEOUT, node, frameworkProject,
                framework);
        }

        public String getUsername() {
            final String user;
            if (null != node.getUsername() || node.containsUserName()) {
                user = node.extractUserName();
            } else {
                user = resolveProperty(WINRM_USER, null, node, frameworkProject, framework);
            }

            if (null != user && user.contains("${")) {
                return DataContextUtils.replaceDataReferences(user, context.getDataContext());
            }
            return user;
        }

        public String getHostname() {
            return node.extractHostname();
        }

        public String getAuthType() {
            return resolveProperty(WINRM_AUTH_TYPE, KERBEROS_AUTH, node, frameworkProject, framework);
        }

        public String getCertTrustStrategy() {
            return resolveProperty(WINRM_CERT_TRUST, null, node, frameworkProject, framework);
        }

        public String getHostnameTrustStrategy() {
            return resolveProperty(WINRM_HOSTNAME_TRUST, null, node, frameworkProject, framework);
        }

        public String getProtocol() {
            return resolveProperty(WINRM_PROTOCOL, DEFAULT_WINRM_PROTOCOL, node, frameworkProject, framework);
        }

        public Boolean isDebugKerberosAuth() {
            return resolveBooleanProperty(DEBUG_KERBEROS_AUTH, DEBUG_KERBEROS_AUTH_DEFAULT, node, frameworkProject,
                framework);
        }

        private int getPort() {
            // If the node entry contains a non-default port, configure the connection to use it.
            if (node.containsPort()) {
                try {
                    return Integer.parseInt(node.extractPort());
                } catch (NumberFormatException e) {
                    throw new ConfigurationException("Port number is not valid: " + node.extractPort(), e);
                }
            } else {
                return resolveIntProperty(WINRM_PORT, DEFAULT_WINRM_PORT, node, frameworkProject, framework);
            }
        }

        public ConnectionOptions build() {
            final ConnectionOptions options = new ConnectionOptions();
            options.set(ADDRESS, getHostname());
            final String authType = getAuthType();
            final CifsConnectionType conType;
            final boolean isHttps = "https".equalsIgnoreCase(getProtocol());
            final boolean isKerberos = KERBEROS_AUTH.equals(authType);
            if (isKerberos) {
                conType = isHttps ? WINRM_HTTPS_KB5 : WINRM_HTTP_KB5;
                options.set(CifsConnectionBuilder.DEBUG_KERBEROS_AUTH, isDebugKerberosAuth());
            } else {
                conType = isHttps ? WINRM_HTTPS : WINRM_HTTP;
            }

            options.set(USERNAME, getUsername());
            final String password = getPassword();
            final boolean valid = null != password && !"".equals(password);
            if (!valid) {
                throw new ConfigurationException("Password was not set");
            }
            options.set(PASSWORD, password);

            if (isHttps && isKerberos) {
                final String certTrustStrategy = getCertTrustStrategy();
                if (null != certTrustStrategy) {
                    options.set(CifsConnectionBuilder.HTTPS_CERTIFICATE_TRUST_STRATEGY, certTrustStrategy);
                }
                final String hostnameTrustStrategy = getHostnameTrustStrategy();
                if (null != hostnameTrustStrategy) {
                    options.set(CifsConnectionBuilder.HTTPS_HOSTNAME_VERIFY_STRATEGY, hostnameTrustStrategy);
                }
            }

            options.set(OPERATING_SYSTEM, WINDOWS);
            options.set(CONNECTION_TYPE, conType);
            options.set(CONNECTION_TIMEOUT_MILLIS, getConnectionTimeout());
            options.set(PORT, getPort());
            return options;
        }
    }

}

