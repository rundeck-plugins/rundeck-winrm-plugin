package com.dtolabs.rundeck.plugin.overthere;


import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.common.FrameworkProject;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.dispatcher.DataContextUtils;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.service.NodeExecutor;
import com.dtolabs.rundeck.core.execution.service.NodeExecutorResult;
import com.dtolabs.rundeck.core.execution.service.NodeExecutorResultImpl;
import com.dtolabs.rundeck.core.execution.workflow.steps.FailureReason;
import com.dtolabs.rundeck.core.execution.workflow.steps.StepFailureReason;
import com.dtolabs.rundeck.core.execution.workflow.steps.node.NodeStepFailureReason;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.Describable;
import com.dtolabs.rundeck.core.plugins.configuration.Description;
import com.dtolabs.rundeck.core.plugins.configuration.PropertyUtil;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;
import com.xebialabs.overthere.*;
import com.xebialabs.overthere.cifs.CifsConnectionBuilder;
import com.xebialabs.overthere.cifs.CifsConnectionType;
import com.xebialabs.overthere.cifs.WinrmHttpsCertificateTrustStrategy;
import com.xebialabs.overthere.cifs.WinrmHttpsHostnameVerificationStrategy;
import com.xebialabs.overthere.cifs.winrm.WinRmRuntimeIOException;
import com.xebialabs.overthere.util.ConsoleOverthereExecutionOutputHandler;
import com.xebialabs.overthere.util.DefaultAddressPortMapper;

import java.util.*;

import static com.xebialabs.overthere.ConnectionOptions.*;
import static com.xebialabs.overthere.OperatingSystemFamily.WINDOWS;

@Plugin(name = OTWinRMNodeExecutor.SERVICE_PROVIDER_TYPE, service = "NodeExecutor")
public class OTWinRMNodeExecutor implements NodeExecutor, Describable {
    public static final String SERVICE_PROVIDER_TYPE = "overthere-winrm";

    public static final int DEFAULT_WINRM_CONNECTION_TIMEOUT = 15000;
    public static final String WINRM_PASSWORD_OPTION = "winrm-password-option";
    public static final String DEFAULT_WINRM_PASSWORD_OPTION = "option.winrmPassword";
    public static final int DEFAULT_HTTPS_PORT = 5986;
    public static final int DEFAULT_HTTP_PORT = 5985;
    public static final String WINRM_CONNECTION_TIMEOUT_PROPERTY = "winrm-connection-timeout";
    public static final String WINRM_USER = "winrm-user";
    public static final String WINRM_PORT = "winrm-port";
    public static final String WINRM_AUTH_TYPE = "winrm-auth-type";
    public static final String WINRM_CERT_TRUST = "winrm-cert-trust";
    public static final String WINRM_HOSTNAME_TRUST = "winrm-hostname-trust";
    public static final String WINRM_PROTOCOL = "winrm-protocol";
    public static final String AUTH_TYPE_KERBEROS = "kerberos";
    public static final String AUTH_TYPE_BASIC = "basic";
    public static final String WINRM_PROTOCOL_HTTPS = "https";
    public static final String WINRM_PROTOCOL_HTTP = "http";
    public static final String WINRM_SPN_ADD_PORT = "winrm-spn-add-port";
    public static final String WINRM_SPN_USE_HTTP = "winrm-spn-use-http";
    public static final String WINRM_LOCALE = "winrm-locale";
    public static final String WINRM_TIMEOUT = "winrm-timeout";

    public static final String HOSTNAME_TRUST_BROWSER_COMPATIBLE = "browser-compatible";
    public static final String HOSTNAME_TRUST_STRICT= "strict";
    public static final String HOSTNAME_TRUST_ALL= "all";


    public static final String CERT_TRUST_DEFAULT = "default";
    public static final String CERT_TRUST_ALL= "all";
    public static final String CERT_TRUST_SELF_SIGNED= "self-signed";

    public static final String DEFAULT_AUTH_TYPE = AUTH_TYPE_KERBEROS;
    public static final String DEBUG_KERBEROS_AUTH = "winrm-kerberos-debug";
    public static final Boolean DEFAULT_DEBUG_KERBEROS_AUTH = false;
    public static final String DEFAULT_WINRM_PROTOCOL = WINRM_PROTOCOL_HTTPS;

    public static final WinrmHttpsCertificateTrustStrategy DEFAULT_CERT_TRUST= WinrmHttpsCertificateTrustStrategy.STRICT;
    public static final WinrmHttpsHostnameVerificationStrategy DEFAULT_HOSTNAME_VERIFY= WinrmHttpsHostnameVerificationStrategy.BROWSER_COMPATIBLE;

    //Config properties for GUI
    public static final String CONFIG_AUTHENTICATION = "authentication";
    public static final String CONFIG_PROTOCOL= "protocol";
    public static final String CONFIG_CERT_TRUST= "certTrust";
    public static final String CONFIG_HOSTNAME_VERIFY= "hostnameVerify";
    public static final String CONFIG_SPN_ADD_PORT= "spnAddPort";
    public static final String CONFIG_SPN_USE_HTTP= "spnUseHttp";
    public static final String CONFIG_LOCALE= "locale";
    public static final String CONFIG_WINRM_TIMEOUT= "winrmTimeout";
    private static final String CONFIG_TIMEOUT = "timeout";

    private Framework framework;
    private static final String PROJ_PROP_PREFIX = "project.";
    private static final String FWK_PROP_PREFIX = "framework.";

    public OTWinRMNodeExecutor(final Framework framework) {
        this.framework = framework;
    }

    static final Description DESC = DescriptionBuilder.builder()
            .name(SERVICE_PROVIDER_TYPE)
            .title("WinRM")
            .description("Executes a command on a remote windows node via WinRM.")
            .property(PropertyUtil.select(CONFIG_AUTHENTICATION, "Authentication",
                    "Authentication mechanism to use",
                    true, DEFAULT_AUTH_TYPE, Arrays.asList(AUTH_TYPE_KERBEROS, AUTH_TYPE_BASIC)))

            .property(PropertyUtil.select(CONFIG_PROTOCOL, "WinRM Protocol",
                    "HTTP Protocol",
                    true, DEFAULT_WINRM_PROTOCOL, Arrays.asList(WINRM_PROTOCOL_HTTP, WINRM_PROTOCOL_HTTPS)))

            .property(PropertyUtil.select(CONFIG_CERT_TRUST, "HTTPS Certificate Trust",
                    "Strategy for certificate trust (Kerberos only)",
                    false, DEFAULT_CERT_TRUST.toString(), Arrays.asList(CERT_TRUST_ALL, CERT_TRUST_SELF_SIGNED,
                    CERT_TRUST_DEFAULT)))


            .property(PropertyUtil.select(CONFIG_HOSTNAME_VERIFY, "HTTPS Hostname Verification",
                    "Strategy for hostname verification (Kerberos only)",
                    false, DEFAULT_HOSTNAME_VERIFY.toString(), Arrays.asList(HOSTNAME_TRUST_ALL,
                    HOSTNAME_TRUST_BROWSER_COMPATIBLE,
                    HOSTNAME_TRUST_STRICT)))


            .property(PropertyUtil.bool(CONFIG_SPN_ADD_PORT, "SPN adds Port",
                    "If true, add the port (e.g. 5985) to the SPN used for Kerberos Authentication.",
                    true, "false"))

            .property(PropertyUtil.bool(CONFIG_SPN_USE_HTTP, "SPN uses HTTP",
                    "If true, use HTTP instead of WSMAN for the SPN used for Kerberos Authentication.",
                    true, "false"))

            .property(PropertyUtil.string(CONFIG_LOCALE, "WinRM Locale",
                    "Locale, default: en-us.", false, null))

            .property(PropertyUtil.string(CONFIG_WINRM_TIMEOUT, "WinRM Timeout",
                    "WinRM protocol Timeout, in XML Schema Duration format. (Default: PT60.000S) see: <http://www.w3" +
                            ".org/TR/xmlschema-2/#isoformats>", false, null))

            .property(PropertyUtil.longProp(CONFIG_TIMEOUT, "Connection Timeout", "Connection timeout, " +
                    "in milliseconds. Default: 12000 (2 minutes).", false, null))

            .mapping(CONFIG_AUTHENTICATION, PROJ_PROP_PREFIX + WINRM_AUTH_TYPE)
            .mapping(CONFIG_PROTOCOL, PROJ_PROP_PREFIX + WINRM_PROTOCOL)
            .mapping(CONFIG_CERT_TRUST, PROJ_PROP_PREFIX + WINRM_CERT_TRUST)
            .mapping(CONFIG_HOSTNAME_VERIFY, PROJ_PROP_PREFIX + WINRM_HOSTNAME_TRUST)
            .mapping(CONFIG_SPN_ADD_PORT, PROJ_PROP_PREFIX + WINRM_SPN_ADD_PORT)
            .mapping(CONFIG_SPN_USE_HTTP, PROJ_PROP_PREFIX + WINRM_SPN_USE_HTTP)
            .mapping(CONFIG_LOCALE, PROJ_PROP_PREFIX + WINRM_LOCALE)
            .mapping(CONFIG_WINRM_TIMEOUT, PROJ_PROP_PREFIX + WINRM_CONNECTION_TIMEOUT_PROPERTY)
            .mapping(CONFIG_TIMEOUT, PROJ_PROP_PREFIX + WINRM_TIMEOUT)
            .build();

    public Description getDescription() {
        return DESC;
    }

    public static enum Reason implements FailureReason {
        WinRMProtocolError,
    }

    public NodeExecutorResult executeCommand(final ExecutionContext context, final String[] command,
                                             final INodeEntry node) {
        if (null == node.getHostname() || null == node.extractHostname()) {
            return NodeExecutorResultImpl.createFailure(StepFailureReason.ConfigurationFailure,
                    "Hostname must be set to connect to remote node '" + node.getNodename() + "'", node);
        }
        if (null == node.extractUserName()) {
            return NodeExecutorResultImpl.createFailure(StepFailureReason.ConfigurationFailure,
                    "Username must be set to connect to remote node '" + node.getNodename() + "'", node);
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
                result = connection.execute(ConsoleOverthereExecutionOutputHandler.sysoutHandler(),
                        ConsoleOverthereExecutionOutputHandler.syserrHandler(),
                        CmdLine.build(command));
            } finally {
                connection.close();
            }
        } catch (WinRmRuntimeIOException re) {
            if (context.getLoglevel() > 2) {
                re.printStackTrace(System.err);
                message = re.getMessage();
            } else {
                message = "WinRM Error: " + re.getCause();
            }
            context.getExecutionListener().log(0,
                    "[" + SERVICE_PROVIDER_TYPE + "] failed: " + message);
            return NodeExecutorResultImpl.createFailure(Reason.WinRMProtocolError, message, re, node, -1);
        } catch (RuntimeIOException re) {
            if (context.getLoglevel() > 2) {
                re.printStackTrace(System.err);
                message = re.getMessage();
            } else {
                message = "runtime exception: " + re;
            }
            context.getExecutionListener().log(0,
                    "[" + SERVICE_PROVIDER_TYPE + "] failed: " + message);
            return NodeExecutorResultImpl.createFailure(StepFailureReason.IOFailure, message, re, node, -1);
        }


        final int resultCode = result;
        final boolean status = resultCode == 0;

        if (!status) {
            if(null==message) {
                context.getExecutionListener().log(0,
                    "[" + SERVICE_PROVIDER_TYPE + "] failed: exit code: " + resultCode);
            }else{
                context.getExecutionListener().log(0,
                    "[" + SERVICE_PROVIDER_TYPE + "] failed: " + message);
            }
        }
        final String resultmsg = message;

        if (status) {
            return NodeExecutorResultImpl.createSuccess(node);
        } else {
            return NodeExecutorResultImpl.createFailure(NodeStepFailureReason.NonZeroResultCode,
                    "[" + SERVICE_PROVIDER_TYPE + "] result code: " + resultCode +
                            (null != resultmsg ? ": " + resultmsg : ""), node, resultCode);
        }
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
            return resolveIntProperty(WINRM_CONNECTION_TIMEOUT_PROPERTY, DEFAULT_WINRM_CONNECTION_TIMEOUT, node, frameworkProject,
                    framework);
        }
        public static String nonBlank(String input){
            if (null == input || "".equals(input.trim())) {
                return null;
            } else {
                return input.trim();
            }
        }

        public String getUsername() {
            final String user;
            if (null != nonBlank(node.getUsername()) || node.containsUserName()) {
                user = nonBlank(node.getUsername());
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
            return resolveProperty(WINRM_AUTH_TYPE, DEFAULT_AUTH_TYPE, node, frameworkProject, framework);
        }

        public String getCertTrustStrategy() {
            return resolveProperty(WINRM_CERT_TRUST, DEFAULT_CERT_TRUST.toString(), node, frameworkProject, framework);
        }

        public String getHostnameTrustStrategy() {
            return resolveProperty(WINRM_HOSTNAME_TRUST, DEFAULT_HOSTNAME_VERIFY.toString(), node, frameworkProject,
                    framework);
        }

        public String getProtocol() {
            return resolveProperty(WINRM_PROTOCOL, DEFAULT_WINRM_PROTOCOL, node, frameworkProject, framework);
        }

        public Boolean isDebugKerberosAuth() {
            return resolveBooleanProperty(DEBUG_KERBEROS_AUTH, DEFAULT_DEBUG_KERBEROS_AUTH, node, frameworkProject,
                framework);
        }
        public Boolean isWinrmSpnAddPort() {
            return resolveBooleanProperty(WINRM_SPN_ADD_PORT, false, node, frameworkProject,
                framework);
        }
        public Boolean isWinrmSpnUseHttp() {
            return resolveBooleanProperty(WINRM_SPN_USE_HTTP, false, node, frameworkProject,
                framework);
        }

        public String getWinrmLocale() {
            return resolveProperty(WINRM_LOCALE, null, node, frameworkProject, framework);
        }

        public String getWinrmTimeout() {
            return resolveProperty(WINRM_TIMEOUT, null, node, frameworkProject, framework);
        }

        private int getPort(final int defaultPort) {
            // If the node entry contains a non-default port, configure the connection to use it.
            if (node.containsPort()) {
                try {
                    return Integer.parseInt(node.extractPort());
                } catch (NumberFormatException e) {
                    throw new ConfigurationException("Port number is not valid: " + node.extractPort(), e);
                }
            } else {
                return resolveIntProperty(WINRM_PORT, defaultPort, node, frameworkProject, framework);
            }
        }

        public ConnectionOptions build() {
            final ConnectionOptions options = new ConnectionOptions();
            final String authType = getAuthType();
            final boolean isHttps = WINRM_PROTOCOL_HTTPS.equalsIgnoreCase(getProtocol());

            final boolean isKerberos = getUsername().indexOf("@") > 0 || AUTH_TYPE_KERBEROS.equals(authType);
            String username = getUsername();
            if(isKerberos && username.indexOf("@")<0) {
                username = username + "@" + getHostname();
            }

            if (isKerberos) {
                options.set(CifsConnectionBuilder.WINRM_KERBEROS_DEBUG, isDebugKerberosAuth());
                options.set(CifsConnectionBuilder.WINRM_KERBEROS_ADD_PORT_TO_SPN, isWinrmSpnAddPort());
                options.set(CifsConnectionBuilder.WINRM_KERBEROS_USE_HTTP_SPN, isWinrmSpnUseHttp());
            }
            final String password = getPassword();
            final boolean valid = null != password && !"".equals(password);
            if (!valid) {
                throw new ConfigurationException("Password was not set");
            }

            if (isHttps) {
                final String certTrustStrategy = getCertTrustStrategy();
                WinrmHttpsCertificateTrustStrategy strategy = WinrmHttpsCertificateTrustStrategy.valueOf(certTrustStrategy);
                if (null != certTrustStrategy) {
                    options.set(CifsConnectionBuilder.WINRM_HTTPS_CERTIFICATE_TRUST_STRATEGY, strategy);
                }
                final String hostnameTrustStrategy = getHostnameTrustStrategy();
                WinrmHttpsHostnameVerificationStrategy hostStrat = WinrmHttpsHostnameVerificationStrategy.valueOf(hostnameTrustStrategy);
                if (null != hostnameTrustStrategy) {
                    options.set(CifsConnectionBuilder.WINRM_HTTPS_HOSTNAME_VERIFICATION_STRATEGY, hostStrat);
                }
            }

            options.set(ADDRESS, getHostname());
            options.set(USERNAME, username);
            options.set(PASSWORD, password);
            options.set(OPERATING_SYSTEM, WINDOWS);
            options.set(CONNECTION_TIMEOUT_MILLIS, getConnectionTimeout());
            options.set(PORT, getPort(isHttps ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT));
            options.set(CifsConnectionBuilder.CONNECTION_TYPE, CifsConnectionType.WINRM);
            options.set(CifsConnectionBuilder.WINRM_ENABLE_HTTPS, isHttps);
            options.set(CifsConnectionBuilder.WINRM_KERBEROS_ADD_PORT_TO_SPN, isWinrmSpnAddPort());
            options.set(CifsConnectionBuilder.WINRM_KERBEROS_USE_HTTP_SPN, isWinrmSpnUseHttp());
            if(null!=getWinrmLocale()){
                options.set(CifsConnectionBuilder.WINRM_LOCALE, getWinrmLocale());
            }
            if(null!=getWinrmTimeout()){
                options.set(CifsConnectionBuilder.WINRM_TIMEMOUT, getWinrmTimeout());
            }
            return options;
        }
    }

}

