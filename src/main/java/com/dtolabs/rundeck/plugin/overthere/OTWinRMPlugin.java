package com.dtolabs.rundeck.plugin.overthere;

import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.dispatcher.DataContextUtils;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.plugins.configuration.PropertyUtil;
import com.dtolabs.rundeck.core.plugins.configuration.StringRenderingConstants;
import com.dtolabs.rundeck.core.storage.ResourceMeta;
import com.dtolabs.rundeck.plugins.util.DescriptionBuilder;
import com.dtolabs.rundeck.plugins.util.PropertyBuilder;
import com.xebialabs.overthere.ConnectionOptions;
import com.xebialabs.overthere.cifs.CifsConnectionBuilder;
import com.xebialabs.overthere.cifs.CifsConnectionType;
import com.xebialabs.overthere.cifs.WinrmHttpsCertificateTrustStrategy;
import com.xebialabs.overthere.cifs.WinrmHttpsHostnameVerificationStrategy;
import org.rundeck.storage.api.Path;
import org.rundeck.storage.api.PathUtil;
import org.rundeck.storage.api.StorageException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import static com.xebialabs.overthere.ConnectionOptions.*;
import static com.xebialabs.overthere.OperatingSystemFamily.WINDOWS;

class OTWinRMPlugin {
    public static final String AUTH_TYPE_KERBEROS = "kerberos";
    public static final String SERVICE_PROVIDER_TYPE = "overthere-winrm";

    public static final int DEFAULT_WINRM_CONNECTION_TIMEOUT = 15000;
    public static final boolean DEFAULT_WINRM_CONNECTION_ENCRYPTED = true;
    public static final String WINRM_PASSWORD_OPTION = "winrm-password-option";
    public static final String WINRM_USER_OPTION = "winrm-user-option";
    public static final String WINRM_PASSWORD_STORAGE_PATH = "winrm-password-storage-path";
    public static final String DEFAULT_WINRM_PASSWORD_OPTION = "winrmPassword";
    public static final String DEFAULT_WINRM_USER_OPTION = "winrmUser";
    public static final int DEFAULT_HTTPS_PORT = 5986;
    public static final int DEFAULT_HTTP_PORT = 5985;
    public static final String WINRM_CONNECTION_TIMEOUT_PROPERTY = "winrm-connection-timeout";
    public static final String WINRM_PORT = "winrm-port";
    public static final String WINRM_AUTH_TYPE = "winrm-auth-type";
    public static final String WINRM_CERT_TRUST = "winrm-cert-trust";
    public static final String WINRM_HOSTNAME_TRUST = "winrm-hostname-trust";
    public static final String WINRM_PROTOCOL = "winrm-protocol";
    public static final String AUTH_TYPE_BASIC = "basic";
    public static final String WINRM_PROTOCOL_HTTPS = "https";
    public static final String WINRM_PROTOCOL_HTTP = "http";
    public static final String WINRM_SPN_ADD_PORT = "winrm-spn-add-port";
    public static final String WINRM_SPN_USE_HTTP = "winrm-spn-use-http";
    public static final String WINRM_LOCALE = "winrm-locale";
    public static final String WINRM_TIMEOUT = "winrm-timeout";

    public static final String WINRM_CMD_TYPE = "winrm-cmd";

    public static final String WINRM_IS_DOMAIN_MEMBER = "winrm-is-domain-member";
    public static final String WINRM_DOMAIN = "winrm-domain";
    public static final Boolean DEFAULT_IS_DOMAIN_MEMBER = false;

    public static final String HOSTNAME_TRUST_BROWSER_COMPATIBLE = "browser-compatible";
    public static final String HOSTNAME_TRUST_STRICT = "strict";
    public static final String HOSTNAME_TRUST_ALL = "all";

    public static final String FILE_COPY_DESTINATION_DIR = "file-copy-destination-dir";

    public static final String CMD_TYPE_CMD = "CMD";
    public static final String CMD_TYPE_POWERSHELL = "PowerShell";
    public static final String DEFAULT_CMD_TYPE = CMD_TYPE_CMD;


    public static final String CERT_TRUST_DEFAULT = "default";
    public static final String CERT_TRUST_ALL = "all";
    public static final String CERT_TRUST_SELF_SIGNED = "self-signed";

    public static final String DEFAULT_AUTH_TYPE = OTWinRMPlugin.AUTH_TYPE_KERBEROS;
    public static final String DEBUG_KERBEROS_AUTH = "winrm-kerberos-debug";
    public static final Boolean DEFAULT_DEBUG_KERBEROS_AUTH = false;
    public static final String DEFAULT_WINRM_PROTOCOL = WINRM_PROTOCOL_HTTPS;
    public static final String KERBEROS_CACHE = "kerberos-cache";
    public static final Boolean DEFAULT_KERBEROS_CACHE = false;
    public static final String DEFAULT_WINRM_USER = "rundeck";

    public static final WinrmHttpsCertificateTrustStrategy DEFAULT_CERT_TRUST =
            WinrmHttpsCertificateTrustStrategy.STRICT;
    public static final WinrmHttpsHostnameVerificationStrategy DEFAULT_HOSTNAME_VERIFY =
            WinrmHttpsHostnameVerificationStrategy.BROWSER_COMPATIBLE;

    //Config properties for GUI
    public static final String CONFIG_AUTHENTICATION = "authentication";
    public static final String CONFIG_PROTOCOL = "protocol";
    public static final String CONFIG_CERT_TRUST = "certTrust";
    public static final String CONFIG_HOSTNAME_VERIFY = "hostnameVerify";
    public static final String CONFIG_SPN_ADD_PORT = "spnAddPort";
    public static final String CONFIG_SPN_USE_HTTP = "spnUseHttp";
    public static final String CONFIG_CMD_TYPE = "cmd";
    public static final String CONFIG_LOCALE = "locale";
    public static final String CONFIG_WINRM_TIMEOUT = "winrmTimeout";
    public static final String CONFIG_TIMEOUT = "timeout";
    public static final String CONFIG_PASSWORD_STORAGE_PATH = "passwordStoragePath";
    public static final String CONFIG_USER = "userWinrm";
    public static final String CONFIG_DST_FILE = "dstFile";

    public static final String PROJ_PROP_PREFIX = "project.";
    public static final String FWK_PROP_PREFIX = "framework.";

    public static final DescriptionBuilder createDefaultDescriptionBuilder() {
        return DescriptionBuilder.builder()
                .name(SERVICE_PROVIDER_TYPE)
                .title("WinRM")
                .property(PropertyUtil.select(CONFIG_AUTHENTICATION, "Authentication",
                        "Authentication mechanism to use",
                        true, DEFAULT_AUTH_TYPE, Arrays.asList(OTWinRMPlugin.AUTH_TYPE_KERBEROS, AUTH_TYPE_BASIC)))

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
                        "WinRM protocol Timeout, in XML Schema Duration format. (Default: PT60.000S) \n\n" +
                                "see: <http://www.w3" +
                                ".org/TR/xmlschema-2/#isoformats>", false, null))

                .property(PropertyUtil.longProp(CONFIG_TIMEOUT, "Connection Timeout", "Connection timeout, " +
                        "in milliseconds. Default: 15000 (15 seconds).", false, null))
                .property(
                        PropertyBuilder.builder()
                                .string(CONFIG_PASSWORD_STORAGE_PATH)
                                .title("Password Storage")
                                .description(
                                        "Key Storage Path for winrm Password.\n\n" +
                                                "The path can contain property references like `${node.name}`."
                                )
                                .renderingOption(
                                        StringRenderingConstants.SELECTION_ACCESSOR_KEY,
                                        StringRenderingConstants.SelectionAccessor.STORAGE_PATH
                                )
                                .renderingOption(
                                        StringRenderingConstants.STORAGE_PATH_ROOT_KEY,
                                        "keys"
                                )
                                .renderingOption(
                                        StringRenderingConstants.STORAGE_FILE_META_FILTER_KEY,
                                        "Rundeck-data-type=password"
                                )
                                .build()
                )
                .property(PropertyUtil.string(CONFIG_USER, "WinRM User",
                        "User to connect with. Default: rundeck", false, DEFAULT_WINRM_USER))
                .mapping(CONFIG_AUTHENTICATION, PROJ_PROP_PREFIX + WINRM_AUTH_TYPE)
                .mapping(CONFIG_PROTOCOL, PROJ_PROP_PREFIX + WINRM_PROTOCOL)
                .mapping(CONFIG_CERT_TRUST, PROJ_PROP_PREFIX + WINRM_CERT_TRUST)
                .mapping(CONFIG_HOSTNAME_VERIFY, PROJ_PROP_PREFIX + WINRM_HOSTNAME_TRUST)
                .mapping(CONFIG_SPN_ADD_PORT, PROJ_PROP_PREFIX + WINRM_SPN_ADD_PORT)
                .mapping(CONFIG_SPN_USE_HTTP, PROJ_PROP_PREFIX + WINRM_SPN_USE_HTTP)
                .mapping(CONFIG_LOCALE, PROJ_PROP_PREFIX + WINRM_LOCALE)
                .mapping(CONFIG_WINRM_TIMEOUT, PROJ_PROP_PREFIX + WINRM_TIMEOUT)
                .mapping(CONFIG_TIMEOUT, PROJ_PROP_PREFIX + WINRM_CONNECTION_TIMEOUT_PROPERTY)
                .mapping(CONFIG_PASSWORD_STORAGE_PATH, PROJ_PROP_PREFIX + WINRM_PASSWORD_STORAGE_PATH)
                .mapping(CONFIG_USER, PROJ_PROP_PREFIX + WINRM_USER_OPTION);
    }

    /**
     * Resolve a node/project/framework property by first checking node attributes named X, then project properties
     * named "project.X", then framework properties named "framework.X". If none of those exist, return the default
     * value
     */
    public static String resolveProperty(
            final String nodeAttribute,
            final String defaultValue,
            final INodeEntry node,
            final String frameworkProject,
            final Framework framework
    ) {
        if (null != node.getAttributes().get(nodeAttribute)) {
            return node.getAttributes().get(nodeAttribute);
        } else if (
                framework.hasProjectProperty(PROJ_PROP_PREFIX + nodeAttribute, frameworkProject)
                        && !"".equals(framework.getProjectProperty(frameworkProject, PROJ_PROP_PREFIX + nodeAttribute))
                ) {
            return framework.getProjectProperty(frameworkProject, PROJ_PROP_PREFIX + nodeAttribute);
        } else if (framework.hasProperty(FWK_PROP_PREFIX + nodeAttribute)) {
            return framework.getProperty(FWK_PROP_PREFIX + nodeAttribute);
        } else {
            return defaultValue;
        }
    }

    public static int resolveIntProperty(
            final String attribute,
            final int defaultValue,
            final INodeEntry iNodeEntry,
            final String frameworkProject,
            final Framework framework
    ) throws OTWinRMNodeExecutor.ConfigurationException {
        int value = defaultValue;
        final String string = resolveProperty(attribute, null, iNodeEntry, frameworkProject, framework);
        if (null != string) {
            try {
                value = Integer.parseInt(string);
            } catch (NumberFormatException e) {
                throw new OTWinRMNodeExecutor.ConfigurationException("Not a valid integer: " + attribute + ": " + string);
            }
        }
        return value;
    }

    public static long resolveLongProperty(
            final String attribute,
            final long defaultValue,
            final INodeEntry iNodeEntry,
            final String frameworkProject,
            final Framework framework
    ) throws OTWinRMNodeExecutor.ConfigurationException {
        long value = defaultValue;
        final String string = resolveProperty(attribute, null, iNodeEntry, frameworkProject, framework);
        if (null != string) {
            try {
                value = Long.parseLong(string);
            } catch (NumberFormatException e) {
                throw new OTWinRMNodeExecutor.ConfigurationException("Not a valid long: " + attribute + ": " + string);
            }
        }
        return value;
    }

    public static boolean resolveBooleanProperty(
            final String attribute,
            final boolean defaultValue,
            final INodeEntry iNodeEntry,
            final String frameworkProject,
            final Framework framework
    ) {
        boolean value = defaultValue;
        final String string = resolveProperty(attribute, null, iNodeEntry, frameworkProject, framework);
        if (null != string) {
            value = Boolean.parseBoolean(string);
        }
        return value;
    }

    protected static String nonBlank(String input) {
        if (null == input || "".equals(input.trim())) {
            return null;
        } else {
            return input.trim();
        }
    }

    protected static class ConfigurationException extends Exception {
        ConfigurationException(String s) {
            super(s);
        }

        ConfigurationException(String s, Throwable throwable) {
            super(s, throwable);
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
        String dataset = null;
        String optname = null;
        if (null != opts && 2 == opts.length) {
            dataset = opts[0];
            optname = opts[1];
        } else if (null != opts && 1 == opts.length) {
            dataset = "option";
            optname = opts[0];
        }
        final Map<String, String> option = context.getPrivateDataContext().get(dataset);
        if (null != option) {
            return option.get(optname);
        }
        return null;
    }

    protected static class ConnectionOptionsBuilder {

        private ExecutionContext context;
        private INodeEntry node;
        private Framework framework;
        private String frameworkProject;

        ConnectionOptionsBuilder(final ExecutionContext context, final INodeEntry node, final Framework framework) {
            this.context = context;
            this.node = node;
            this.framework = framework;
            this.frameworkProject = context.getFrameworkProject();
        }

        public String getPassword()  throws OTWinRMNodeExecutor.ConfigurationException {
            //look for storage option
            String storagePath = resolveProperty(WINRM_PASSWORD_STORAGE_PATH, null,
                    getNode(), getFrameworkProject(), getFramework());
            if(null!=storagePath){
                //look up storage value
                if (storagePath.contains("${")) {
                    storagePath = DataContextUtils.replaceDataReferences(
                            storagePath,
                            context.getDataContext()
                    );
                }
                Path path = PathUtil.asPath(storagePath);
                try {
                    ResourceMeta contents = context.getStorageTree().getResource(path)
                            .getContents();
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    contents.writeContent(byteArrayOutputStream);
                    return new String(byteArrayOutputStream.toByteArray());
                } catch (StorageException e) {
                    throw new ConfigurationException("Failed to read the winrm password for " +
                            "storage path: " + storagePath + ": " + e.getMessage());
                } catch (IOException e) {
                    throw new ConfigurationException("Failed to read the winrm password for " +
                            "storage path: " + storagePath + ": " + e.getMessage());
                }
            }
            //else look up option value
            final String passwordOption = resolveProperty(WINRM_PASSWORD_OPTION,
                    DEFAULT_WINRM_PASSWORD_OPTION, getNode(),
                    getFrameworkProject(), getFramework());
            return evaluateSecureOption(passwordOption, getContext());
        }

        public int getConnectionTimeout() throws ConfigurationException {
            return resolveIntProperty(WINRM_CONNECTION_TIMEOUT_PROPERTY, DEFAULT_WINRM_CONNECTION_TIMEOUT, getNode(),
                getFrameworkProject(), getFramework());
        }

        public String getUsername() {
            final String user;
            if (null != nonBlank(getNode().getUsername()) || getNode().containsUserName()) {
                user = nonBlank(getNode().getUsername());
            } else {
                user = resolveProperty(WINRM_USER_OPTION, DEFAULT_WINRM_USER_OPTION, getNode(), getFrameworkProject(), getFramework());
            }

            if (null != user && user.contains("${")) {
                return DataContextUtils.replaceDataReferences(user, getContext().getDataContext());
            }
            return user;
        }

        public String getHostname() {
            return getNode().extractHostname();
        }

        public String getAuthType() {
            return resolveProperty(WINRM_AUTH_TYPE, DEFAULT_AUTH_TYPE, getNode(), getFrameworkProject(), getFramework());
        }

        public WinrmHttpsCertificateTrustStrategy getCertTrustStrategy() {
            String trust = resolveProperty(
                    WINRM_CERT_TRUST,
                    DEFAULT_CERT_TRUST.toString(),
                    getNode(),
                    getFrameworkProject(),
                    getFramework()
            );
            if (trust == null) {
                return DEFAULT_CERT_TRUST;
            }
            if ( trust.equals(CERT_TRUST_DEFAULT))
            {
                return WinrmHttpsCertificateTrustStrategy.STRICT;
            }
            if ( trust.equals(CERT_TRUST_SELF_SIGNED))
            {
                return WinrmHttpsCertificateTrustStrategy.SELF_SIGNED;
            }
            if ( trust.equals(CERT_TRUST_ALL))
            {
                return WinrmHttpsCertificateTrustStrategy.ALLOW_ALL;
            }
            return DEFAULT_CERT_TRUST;
        }

        public WinrmHttpsHostnameVerificationStrategy getHostTrust() {
            String trust = resolveProperty(
                    WINRM_HOSTNAME_TRUST,
                    DEFAULT_HOSTNAME_VERIFY.toString(),
                    getNode(),
                    getFrameworkProject(),
                    getFramework()
            );
            if (trust == null) {
                return DEFAULT_HOSTNAME_VERIFY;
            }
            if ( trust.equals(HOSTNAME_TRUST_STRICT) )
            {
                return WinrmHttpsHostnameVerificationStrategy.STRICT;
            }
            if ( trust.equals(HOSTNAME_TRUST_BROWSER_COMPATIBLE) )
            {
                return WinrmHttpsHostnameVerificationStrategy.BROWSER_COMPATIBLE;
            }
            if ( trust.equals(HOSTNAME_TRUST_ALL) )
            {
                return WinrmHttpsHostnameVerificationStrategy.ALLOW_ALL;
            }
            return DEFAULT_HOSTNAME_VERIFY;
        }

        public String getProtocol() {
            return resolveProperty(
                    WINRM_PROTOCOL,
                    DEFAULT_WINRM_PROTOCOL,
                    getNode(),
                    getFrameworkProject(),
                    getFramework()
            );
        }

        public Boolean isDebugKerberosAuth() {
            return resolveBooleanProperty(
                    DEBUG_KERBEROS_AUTH,
                    DEFAULT_DEBUG_KERBEROS_AUTH,
                    getNode(),
                    getFrameworkProject(),
                    getFramework()
            );
        }

        public Boolean isDomainMember() {
          return resolveBooleanProperty(WINRM_IS_DOMAIN_MEMBER, DEFAULT_IS_DOMAIN_MEMBER, getNode(), getFrameworkProject(),
              getFramework());
        }
        public String getDomain() {
            return resolveProperty(WINRM_DOMAIN, null, getNode(), getFrameworkProject(), getFramework());
        }

        public Boolean isWinrmSpnAddPort() {
            return resolveBooleanProperty(WINRM_SPN_ADD_PORT, false, getNode(), getFrameworkProject(),
                    getFramework());
        }

        public Boolean isWinrmSpnUseHttp() {
            return resolveBooleanProperty(WINRM_SPN_USE_HTTP, false, getNode(), getFrameworkProject(),
                    getFramework());
        }

        public String getWinrmLocale() {
            return resolveProperty(WINRM_LOCALE, null, getNode(), getFrameworkProject(), getFramework());
        }

        public String getWinrmTimeout() {
            return resolveProperty(WINRM_TIMEOUT, null, getNode(), getFrameworkProject(), getFramework());
        }

        public Boolean isKerberosCacheEnabled() {
            return resolveBooleanProperty(KERBEROS_CACHE, DEFAULT_KERBEROS_CACHE, getNode(), getFrameworkProject(),
                    getFramework());
        }

        private int getPort(final int defaultPort) throws ConfigurationException {
            // If the node entry contains a non-default port, configure the connection to use it.
            if (getNode().containsPort()) {
                try {
                    return Integer.parseInt(getNode().extractPort());
                } catch (NumberFormatException e) {
                    throw new ConfigurationException("Port number is not valid: " + getNode().extractPort(), e);
                }
            } else {
                return resolveIntProperty(WINRM_PORT, defaultPort, getNode(), getFrameworkProject(), getFramework());
            }
        }

        public ConnectionOptions build() throws ConfigurationException {
            final ConnectionOptions options = new ConnectionOptions();
            final String authType = getAuthType();
            final boolean isHttps = WINRM_PROTOCOL_HTTPS.equalsIgnoreCase(getProtocol());

            final boolean isKerberos = getUsername().indexOf("@") > 0 || AUTH_TYPE_KERBEROS.equals(authType);

            String username;
            if (isKerberos) {
                username = getKerberosUsername(this);
                options.set(CifsConnectionBuilder.WINRM_KERBEROS_DEBUG, isDebugKerberosAuth());
                options.set(CifsConnectionBuilder.WINRM_KERBEROS_ADD_PORT_TO_SPN, isWinrmSpnAddPort());
                options.set(CifsConnectionBuilder.WINRM_KERBEROS_USE_HTTP_SPN, isWinrmSpnUseHttp());
                options.set(CifsConnectionBuilder.WINRM_KERBEROS_TICKET_CACHE, isKerberosCacheEnabled());
            } else {
                username = getUsername();
            }
            final String password = getClearAuthPassword(this);
            final boolean valid = null != password && !"".equals(password);
            if (!valid) {
                throw new ConfigurationException("Password was not set");
            }

            if (isHttps) {
                options.set(CifsConnectionBuilder.WINRM_HTTPS_CERTIFICATE_TRUST_STRATEGY, getCertTrustStrategy());
                options.set(CifsConnectionBuilder.WINRM_HTTPS_HOSTNAME_VERIFICATION_STRATEGY, getHostTrust());
            }

            options.set(ADDRESS, getHostname());
            options.set(USERNAME, username);
            options.set(PASSWORD, password);
            options.set(OPERATING_SYSTEM, WINDOWS);
            options.set(CONNECTION_TIMEOUT_MILLIS, getConnectionTimeout());
            options.set(PORT, getPort(isHttps ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT));
            options.set(CifsConnectionBuilder.CONNECTION_TYPE, CifsConnectionType.WINRM_INTERNAL);
            options.set(CifsConnectionBuilder.WINRM_ENABLE_HTTPS, isHttps);
            options.set(CifsConnectionBuilder.WINRM_KERBEROS_ADD_PORT_TO_SPN, isWinrmSpnAddPort());
            options.set(CifsConnectionBuilder.WINRM_KERBEROS_USE_HTTP_SPN, isWinrmSpnUseHttp());
            if (null != getWinrmLocale()) {
                options.set(CifsConnectionBuilder.WINRM_LOCALE, getWinrmLocale());
            }
            if (null != getWinrmTimeout()) {
                options.set(CifsConnectionBuilder.WINRM_TIMEMOUT, getWinrmTimeout());
            }
            return options;
        }

        public ExecutionContext getContext() {
            return context;
        }

        public INodeEntry getNode() {
            return node;
        }

        public Framework getFramework() {
            return framework;
        }

        public String getFrameworkProject() {
            return frameworkProject;
        }

        static String getClearAuthPassword(final ConnectionOptionsBuilder options) throws ConfigurationException {
            return options.getPassword();
        }

        static String getKerberosUsername(final ConnectionOptionsBuilder options) throws ConfigurationException {
            String username = options.getUsername();
            String hostname = options.getHostname();
            if (username.indexOf("@") < 0 && null != options.getDomain()) {
                username = username + "@" + options.getDomain().toUpperCase();
            } else if (username.indexOf("@") < 0) {
                String domain = hostname.toUpperCase();
                if(domain.contains(":")) {
                    //remove port if present
                    domain = domain.substring(0, domain.indexOf(":"));
                }
                int domainNameIndex = domain.indexOf(".") + 1;

                if (options.isDomainMember()) {
                    if (domainNameIndex == 0 || domainNameIndex >= domain.length()) {
                        throw new ConfigurationException(
                                "Node FQDN is not correct for configuration as domain member (no '.' found:" +
                                        domain +
                                        ")"
                        );
                    } else {
                        domain = domain.substring(domainNameIndex);
                    }
                }
                username = username + "@" + domain;
            } else if (username.indexOf("@") > 0) {
                String domain = username.substring(username.indexOf("@"));
                username = username.substring(0, username.indexOf("@")) + domain.toUpperCase();
            }
            return username;
        }
    }

    protected Framework framework;

    protected OTWinRMPlugin(Framework framework) {
        this.framework = framework;
    }

    /**
     * Return the cleartext user password
     *
     * @return
     */
    protected String getClearAuthPassword(final ConnectionOptionsBuilder options) throws ConfigurationException {
        return ConnectionOptionsBuilder.getClearAuthPassword(options);
    }

    /**
     * Return the full username@domain to use for kerberos authentication. The default implementation will append
     * "@HOSTNAME" if no "@DOMAIN" is present, otherwise it will convert "@domain" to "@DOMAIN" and return the value.
     * if winrm-is-domain-member is true, the hostname will be reduced by one part of the fqdn, from host.domain.tld to
     * domain.tld, and user@DOMAIN.TLD returned. If winrm-domain is set, it will be used.
     *
     * @param options options builder
     *
     * @return
     */
    protected String getKerberosUsername(final ConnectionOptionsBuilder options) throws ConfigurationException {
        return ConnectionOptionsBuilder.getKerberosUsername(options);
    }

    /**
     * Called before connecting with the ConnectionnOptions that will be used, which can be altered here by a subclass.
     *
     * @param options configured options
     *
     * @return the ConnectionOptions to use
     */
    protected ConnectionOptions willUseConnectionOptions(final ConnectionOptions options) throws ConfigurationException {
        return options;
    }

}
