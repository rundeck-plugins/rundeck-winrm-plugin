package com.dtolabs.rundeck.plugin.overthere;

import com.dtolabs.rundeck.core.Constants;
import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.common.INodeEntry;
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
import com.xebialabs.overthere.CmdLine;
import com.xebialabs.overthere.ConnectionOptions;
import com.xebialabs.overthere.OverthereConnection;
import com.xebialabs.overthere.RuntimeIOException;
import com.xebialabs.overthere.cifs.CifsConnectionBuilder;
import com.xebialabs.overthere.winrm.WinRmRuntimeIOException;
import com.xebialabs.overthere.util.ConsoleOverthereExecutionOutputHandler;
import com.xebialabs.overthere.util.DefaultAddressPortMapper;

import java.util.Arrays;

/**
 * Executes a command via WinRM. Subclass to extend, there are places to alter the generation of user@realm, and to
 * alter the password resolution mechanism.
 * See {@link #getKerberosUsername(com.dtolabs.rundeck.plugin.overthere.OTWinRMPlugin.ConnectionOptionsBuilder)}
 * and
 * {@link #getClearAuthPassword(com.dtolabs.rundeck.plugin.overthere.OTWinRMPlugin.ConnectionOptionsBuilder)}.
 *
 * The connection options can be altered as well by overriding {@link #willUseConnectionOptions(com.xebialabs.overthere.ConnectionOptions)}.
 */
@Plugin(name = OTWinRMPlugin.SERVICE_PROVIDER_TYPE, service = "NodeExecutor")
public class OTWinRMNodeExecutor extends OTWinRMPlugin implements NodeExecutor, Describable {

    public OTWinRMNodeExecutor(final Framework framework) {
        super(framework);
    }

    static final Description DESC = createDefaultDescriptionBuilder()
            .description("Executes a command on a remote windows node via WinRM.")
            .property(PropertyUtil.select(CONFIG_CMD_TYPE, "Command type",
                    String.format("Supported command types: %s, %s", CMD_TYPE_CMD, CMD_TYPE_POWERSHELL),
                    true, DEFAULT_CMD_TYPE, Arrays.asList(CMD_TYPE_CMD, CMD_TYPE_POWERSHELL)))
            .mapping(CONFIG_CMD_TYPE, PROJ_PROP_PREFIX + WINRM_CMD_TYPE)
            .build();

    public Description getDescription() {
        return DESC;
    }

    public static enum Reason implements FailureReason {
        WinRMProtocolError,
    }

    public NodeExecutorResult executeCommand(final ExecutionContext context, final String[] command,
            final INodeEntry node) {

        ConnectionOptions options = null;
        String logprompt = "[" + SERVICE_PROVIDER_TYPE + ":" + node.extractHostname() + "] ";

        if (null == context.getExecutionListener()) {
            System.out.println(logprompt + " Bad plugin context!  NULL ExecutionListener");
        }

        String cmdType;
        try {
            ConnectionOptionsBuilder builder = new ConnectionOptionsBuilder(context, node, framework);
            ConnectionOptions protoOptions = builder.build();
            protoOptions.set(WINRM_CMD_TYPE, resolveProperty(WINRM_CMD_TYPE, null, builder.getNode(), builder.getFrameworkProject(), builder.getFramework()));
            options = willUseConnectionOptions(protoOptions);
            cmdType = options.get(WINRM_CMD_TYPE);
            context.getExecutionListener().log(Constants.VERBOSE_LEVEL, logprompt + buildCommandLine(command, cmdType));
        } catch (ConfigurationException e) {
            context.getExecutionListener().log(Constants.ERR_LEVEL, logprompt + e.getMessage());
            return NodeExecutorResultImpl.createFailure(StepFailureReason.ConfigurationFailure, e.getMessage(), node);
        }

        context.getExecutionListener().log(Constants.VERBOSE_LEVEL, logprompt + options);

        int result = -1;
        try {
            final OverthereConnection connection = new CifsConnectionBuilder(CifsConnectionBuilder.CIFS_PROTOCOL, options,
                    new DefaultAddressPortMapper()).connect();

            try {
                result = connection.execute(ConsoleOverthereExecutionOutputHandler.sysoutHandler(),
                        ConsoleOverthereExecutionOutputHandler.syserrHandler(),
                        buildCommandLine(command, cmdType));
            } finally {
                connection.close();
            }
        } catch (WinRmRuntimeIOException re) {
            String message = null;
            if (context.getLoglevel() > 2) {
                re.printStackTrace(System.err);
                message = re.getMessage();
            } else {
                message = "WinRM Error: " + re.getMessage();
            }
            context.getExecutionListener().log(Constants.ERR_LEVEL, logprompt + "failed: " + message);
            return NodeExecutorResultImpl.createFailure(Reason.WinRMProtocolError, message, re, node, -1);
        } catch (RuntimeIOException re) {
            String message = null;
            if (context.getLoglevel() > 2) {
                re.printStackTrace(System.err);
                message = re.getMessage();
            } else {
                message = "runtime exception: " + re;
            }
            context.getExecutionListener().log(Constants.ERR_LEVEL, logprompt + "failed: " + message);
            return NodeExecutorResultImpl.createFailure(StepFailureReason.IOFailure, message, re, node, -1);
        }


        final int resultCode = result;
        final boolean status = resultCode == 0;

        if (status) {
            return NodeExecutorResultImpl.createSuccess(node);
        } else {
            context.getExecutionListener().log(Constants.ERR_LEVEL, logprompt + "failed: exit code: " + resultCode);
            return NodeExecutorResultImpl.createFailure(NodeStepFailureReason.NonZeroResultCode,
                    "[" + SERVICE_PROVIDER_TYPE + "] result code: " + resultCode, node, resultCode);
        }
    }

    /**
     * Create the {@link CmdLine} to run from the input string array, the default behavior is to use {@link #buildCmdLineRaw(String...)}
     * @param command
     * @return
     */
    protected CmdLine buildCommandLine(String[] command, String OptionCmdLine) {
        if (OptionCmdLine.equals(CMD_TYPE_POWERSHELL))
            return buildCmdLinePowershell(command);
        else
            return buildCmdLineRaw(command);
    }

    /**
     * Build a CmdLine without escaping any part, using Raw arguments
     *
     * @param args the regular arguments which will be added without escaping
     *
     * @return the created command line
     */
    public static CmdLine buildCmdLineRaw(String... args) {
        CmdLine cmdLine = new CmdLine();
        for (String s : args) {
            cmdLine.addRaw(s);
        }
        return cmdLine;
    }

    public static CmdLine buildCmdLinePowershell(String... args) {
        CmdLine cmdLine = new CmdLine();
        cmdLine.addRaw("powershell");
        StringBuilder command = new StringBuilder("");
        for (String s : args) {
            command.append(s.replaceAll("\"", "\\\\\"")).append(" ");
        }
        cmdLine.addRaw(command.toString());
        return cmdLine;
    }
}

