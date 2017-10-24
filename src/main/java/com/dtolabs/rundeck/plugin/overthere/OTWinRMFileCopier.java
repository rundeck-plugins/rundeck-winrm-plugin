package com.dtolabs.rundeck.plugin.overthere;

import com.dtolabs.rundeck.core.Constants;
import com.dtolabs.rundeck.core.common.Framework;
import com.dtolabs.rundeck.core.common.INodeEntry;
import com.dtolabs.rundeck.core.execution.ExecutionContext;
import com.dtolabs.rundeck.core.execution.service.DestinationFileCopier;
import com.dtolabs.rundeck.core.execution.service.FileCopierException;
import com.dtolabs.rundeck.core.execution.workflow.steps.FailureReason;
import com.dtolabs.rundeck.core.execution.workflow.steps.StepFailureReason;
import com.dtolabs.rundeck.core.plugins.Plugin;
import com.dtolabs.rundeck.core.plugins.configuration.Describable;
import com.dtolabs.rundeck.core.plugins.configuration.Description;
import com.dtolabs.rundeck.core.plugins.configuration.PropertyUtil;
import com.xebialabs.overthere.ConnectionOptions;
import com.xebialabs.overthere.OverthereConnection;
import com.xebialabs.overthere.OverthereFile;
import com.xebialabs.overthere.RuntimeIOException;
import com.xebialabs.overthere.cifs.CifsConnectionBuilder;
import com.xebialabs.overthere.winrm.WinRmRuntimeIOException;
import com.xebialabs.overthere.util.DefaultAddressPortMapper;
import org.apache.commons.compress.utils.IOUtils;

import java.io.*;

/**
 * Executes a command via WinRM. Subclass to extend, there are places to alter the generation of user@realm, and to
 * alter the password resolution mechanism.
 *
 * The connection options can be altered as well by overriding {@link #willUseConnectionOptions(ConnectionOptions)}.
 */
@Plugin(name = OTWinRMPlugin.SERVICE_PROVIDER_TYPE, service = "FileCopier")
public class OTWinRMFileCopier extends OTWinRMPlugin implements DestinationFileCopier, Describable {

    public OTWinRMFileCopier(final Framework framework) {
        super(framework);
    }

    static final Description DESC = createDefaultDescriptionBuilder()
            .description("Copies a file on a remote windows node via WinRM.")
            .property(PropertyUtil.string(CONFIG_DST_FILE, "Destination file",
                    "Destination folder for remote scripts", false, null))
            .mapping(CONFIG_DST_FILE, PROJ_PROP_PREFIX + FILE_COPY_DESTINATION_DIR)
            .build();

    public Description getDescription() {
        return DESC;
    }


    public String buildErrorMessage(ExecutionContext context, Exception ex, String logprompt) {
        String message = null;
        if (context.getLoglevel() > 2) {
            ex.printStackTrace(System.err);
            message = ex.getMessage();
        } else {
            message = "WinRM Error: " + ex.getMessage();
        }
        context.getExecutionListener().log(Constants.ERR_LEVEL, logprompt + "failed: " + message);
        return message;
    }

    @Override
    public String copyFileStream(ExecutionContext context, InputStream inputStream, INodeEntry node, String destination) throws FileCopierException {
        ConnectionOptions options = null;
        String logprompt = "[" + SERVICE_PROVIDER_TYPE + ":" + node.extractHostname() + "] ";

        if (null == context.getExecutionListener()) {
            System.out.println(logprompt + " Bad plugin context!  NULL ExecutionListener");
        }

        context.getExecutionListener().log(Constants.VERBOSE_LEVEL, logprompt);

        try {
            ConnectionOptionsBuilder builder = new ConnectionOptionsBuilder(context, node, framework);
            ConnectionOptions protoOptions = builder.build();
            protoOptions.set(FILE_COPY_DESTINATION_DIR, resolveProperty(FILE_COPY_DESTINATION_DIR, null, builder.getNode(), builder.getFrameworkProject(), builder.getFramework()));
            options = willUseConnectionOptions(protoOptions);
        } catch (ConfigurationException e) {
            context.getExecutionListener().log(Constants.ERR_LEVEL, logprompt + e.getMessage());
            throw new FileCopierException(e.getMessage(), StepFailureReason.ConfigurationFailure);
        }

        context.getExecutionListener().log(Constants.VERBOSE_LEVEL, logprompt + options);

        try {
            final OverthereConnection connection = new CifsConnectionBuilder(CifsConnectionBuilder.CIFS_PROTOCOL, options,
                    new DefaultAddressPortMapper()).connect();

            try {
                OverthereFile dst;
                if (destination != null && !destination.isEmpty()) {
                    dst = connection.getFile(destination);
                    context.getExecutionListener().log(Constants.VERBOSE_LEVEL, logprompt + "Destination file acquired " + dst.getPath());
                } else {
                    dst = connection.getTempFile("RD_WINRM_TMP");
                    context.getExecutionListener().log(Constants.VERBOSE_LEVEL, logprompt + "Temporary file acquired " + dst.getPath());
                }

                context.getExecutionListener().log(Constants.VERBOSE_LEVEL, logprompt + "Copying file to " + dst.getPath());
                try {
                    IOUtils.copy(inputStream, dst.getOutputStream());
                } catch (IOException ioe) {
                    context.getExecutionListener().log(Constants.ERR_LEVEL, logprompt + "Error while copying the file");
                    throw new FileCopierException(buildErrorMessage(context, ioe, logprompt), Reason.IOWriteError);
                }
                context.getExecutionListener().log(Constants.VERBOSE_LEVEL, logprompt + "File copied at: " + dst.getPath());
                return dst.getPath();

            } finally {
                context.getExecutionListener().log(Constants.VERBOSE_LEVEL, logprompt + "Closing connection");
                connection.close();
            }
        } catch (WinRmRuntimeIOException re) {
            throw new FileCopierException(buildErrorMessage(context, re, logprompt), Reason.WinRMProtocolError);
        } catch (RuntimeIOException re) {
            throw new FileCopierException(buildErrorMessage(context, re, logprompt), StepFailureReason.IOFailure);
        }

    }

    @Override
    public String copyFile(ExecutionContext context, File file, INodeEntry node, String destination) throws FileCopierException {
        try {
            InputStream fileStream = new FileInputStream(file);
            return copyFileStream(context, fileStream, node, destination);
        } catch (IOException ioe) {
            throw new FileCopierException(ioe.getMessage(), Reason.FileNotStreamableError);
        }
    }

    @Override
    public String copyScriptContent(ExecutionContext context, String script, INodeEntry node, String destination) throws FileCopierException {
        InputStream scriptStream = new ByteArrayInputStream(script.getBytes());
        return copyFileStream(context, scriptStream, node, destination);
    }

    @Override
    public String copyFileStream(ExecutionContext context, InputStream inputStream, INodeEntry node) throws FileCopierException {
        return copyFileStream(context, inputStream, node, null);
    }

    @Override
    public String copyFile(ExecutionContext context, File file, INodeEntry node) throws FileCopierException {
        return copyFile(context, file, node, null);
    }

    @Override
    public String copyScriptContent(ExecutionContext context, String script, INodeEntry node) throws FileCopierException {
        return copyScriptContent(context, script, node, null);
    }

    public static enum Reason implements FailureReason {
        WinRMProtocolError,
        FileNotStreamableError,
        IOWriteError,
    }

}