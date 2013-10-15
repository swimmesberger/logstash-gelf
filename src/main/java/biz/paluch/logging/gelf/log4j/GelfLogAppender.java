package biz.paluch.logging.gelf.log4j;

import biz.paluch.logging.gelf.LogMessageField;
import biz.paluch.logging.gelf.MdcGelfMessageAssembler;
import biz.paluch.logging.gelf.MdcMessageField;
import biz.paluch.logging.gelf.StaticMessageField;
import biz.paluch.logging.gelf.intern.GelfMessage;
import biz.paluch.logging.gelf.intern.GelfSender;
import biz.paluch.logging.gelf.intern.GelfSenderFactory;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;

/**
 * Logging-Handler for GELF (Graylog Extended Logging Format). This Java-Util-Logging Handler creates GELF Messages and posts
 * them using UDP (default) or TCP. Following parameters are supported/needed:
 * <p/>
 * <ul>
 * <li>host (Mandatory): Hostname/IP-Address of the Logstash Host
 * <ul>
 * <li>tcp:(the host) for TCP, e.g. tcp:127.0.0.1 or tcp:some.host.com</li>
 * <li>udp:(the host) for UDP, e.g. udp:127.0.0.1 or udp:some.host.com</li>
 * <li>(the host) for UDP, e.g. 127.0.0.1 or some.host.com</li>
 * </ul>
 * </li>
 * <li>port (Optional): Port, default 12201</li>
 * <li>originHost (Optional): Originating Hostname, default FQDN Hostname</li>
 * <li>extractStackTrace (Optional): Post Stack-Trace to StackTrace field, default false</li>
 * <li>filterStackTrace (Optional): Perform Stack-Trace filtering (true/false), default false</li>
 * <li>mdcProfiling (Optional): Perform Profiling (Call-Duration) based on MDC Data. See <a href="#mdcProfiling">MDC
 * Profiling</a>, default false</li>
 * <li>facility (Optional): Name of the Facility, default gelf-java</li>
 * <li>threshold (Optional): Log-Level, default INFO</li>
 * <li>filter (Optional): Class-Name of a Log-Filter, default none</li>
 * <li>additionalFields(number) (Optional): Post additional fields. Eg.
 * .GelfLogHandler.additionalFields=fieldName=Value,field2=value2</li>
 * <li>mdcFields (Optional): Post additional fields, pull Values from MDC. Name of the Fields are comma-separated
 * mdcFields=Application,Version,SomeOtherFieldName</li>
 * </ul>
 * <p/>
 * <a name="mdcProfiling"></a>
 * <h2>MDC Profiling</h2>
 * <p>
 * MDC Profiling allows to calculate the runtime from request start up to the time until the log message was generated. You must
 * set one value in the MDC:
 * <ul>
 * <li>profiling.requestStart.millis: Time Millis of the Request-Start (Long or String)</li>
 * </ul>
 * <p/>
 * Two values are set by the Log Appender:
 * <ul>
 * <li>profiling.requestEnd: End-Time of the Request-End in Date.toString-representation</li>
 * <li>profiling.requestDuration: Duration of the request (e.g. 205ms, 16sec)</li>
 * </ul>
 * <p/>
 * </p>
 */
public class GelfLogAppender extends AppenderSkeleton {

    protected GelfSender gelfSender;
    protected MdcGelfMessageAssembler gelfMessageAssembler;

    public GelfLogAppender() {
        gelfMessageAssembler = new MdcGelfMessageAssembler();
        gelfMessageAssembler.addFields(LogMessageField.getDefaultMapping());
    }

    @Override
    protected void append(LoggingEvent event) {
        if (event == null) {
            return;
        }

        if (null == gelfSender) {
            if (gelfMessageAssembler.getHost() == null) {
                reportError("Graylog2 hostname is empty!", null);
            } else {
                try {
                    this.gelfSender = GelfSenderFactory.createSender(gelfMessageAssembler.getHost(),
                            gelfMessageAssembler.getPort());
                } catch (UnknownHostException e) {
                    reportError("Unknown Graylog2 hostname:" + gelfMessageAssembler.getHost(), e);
                } catch (SocketException e) {
                    reportError("Socket exception", e);
                } catch (IOException e) {
                    reportError("IO exception", e);
                }
            }
        }

        try {
            GelfMessage message = createGelfMessage(event);
            if (!message.isValid()) {
                reportError("GELF Message is invalid: " + message.toJson(), null);
            }

            if (null == gelfSender || !gelfSender.sendMessage(message)) {
                reportError("Could not send GELF message", null);
            }
        } catch (Exception e) {
            reportError("Could not send GELF message", e);
        }
    }

    private void reportError(String message, Exception exception) {
        errorHandler.error(message, exception, 0);
    }

    @Override
    public boolean requiresLayout() {
        return false;
    }

    @Override
    public void close() {
        if (null != gelfSender) {
            gelfSender.close();
            gelfSender = null;
        }
    }

    protected GelfMessage createGelfMessage(final LoggingEvent loggingEvent) {
        return gelfMessageAssembler.createGelfMessage(new Log4jLogEvent(loggingEvent));
    }

    public void setAdditionalFields(String fieldSpec) {

        String[] properties = fieldSpec.split(",");

        for (String field : properties) {
            final int index = field.indexOf('=');
            if (-1 != index) {
                gelfMessageAssembler.addField(new StaticMessageField(field.substring(0, index), field.substring(index + 1)));
            }
        }
    }

    public void setMdcFields(String fieldSpec) {
        String[] fields = fieldSpec.split(",");

        for (String field : fields) {
            gelfMessageAssembler.addField(new MdcMessageField(field.trim(), field.trim()));
        }
    }

    public String getGraylogHost() {
        return gelfMessageAssembler.getHost();
    }

    public void setGraylogHost(String graylogHost) {
        gelfMessageAssembler.setHost(graylogHost);
    }

    public int getGraylogPort() {
        return gelfMessageAssembler.getPort();
    }

    public void setGraylogPort(int graylogPort) {
        gelfMessageAssembler.setPort(graylogPort);
    }

    public String getHost() {
        return gelfMessageAssembler.getHost();
    }

    public void setHost(String host) {
        gelfMessageAssembler.setHost(host);
    }

    public int getPort() {
        return gelfMessageAssembler.getPort();
    }

    public void setPort(int port) {
        gelfMessageAssembler.setPort(port);
    }

    public String getOriginHost() {
        return gelfMessageAssembler.getOriginHost();
    }

    public void setOriginHost(String originHost) {
        gelfMessageAssembler.setOriginHost(originHost);
    }

    public String getFacility() {
        return gelfMessageAssembler.getFacility();
    }

    public void setFacility(String facility) {
        gelfMessageAssembler.setFacility(facility);
    }

    public boolean isExtractStackTrace() {
        return gelfMessageAssembler.isExtractStackTrace();
    }

    public void setExtractStackTrace(boolean extractStacktrace) {
        gelfMessageAssembler.setExtractStackTrace(extractStacktrace);
    }

    public boolean isFilterStackTrace() {
        return gelfMessageAssembler.isFilterStackTrace();
    }

    public void setFilterStackTrace(boolean filterStackTrace) {
        gelfMessageAssembler.setFilterStackTrace(filterStackTrace);
    }

    public boolean isMdcProfiling() {
        return gelfMessageAssembler.isMdcProfiling();
    }

    public void setMdcProfiling(boolean mdcProfiling) {
        gelfMessageAssembler.setMdcProfiling(mdcProfiling);
    }

    public String getTimestampPattern() {
        return gelfMessageAssembler.getTimestampPattern();
    }

    public void setTimestampPattern(String timestampPattern) {
        gelfMessageAssembler.setTimestampPattern(timestampPattern);
    }

    public int getMaximumMessageSize() {
        return gelfMessageAssembler.getMaximumMessageSize();
    }

    public void setMaximumMessageSize(int maximumMessageSize) {
        gelfMessageAssembler.setMaximumMessageSize(maximumMessageSize);
    }

    public void setTestSenderClass(String testSender) {
        // This only used for testing
        try {
            if (null != testSender) {
                final Class clazz = Class.forName(testSender);
                gelfSender = (GelfSender) clazz.newInstance();
            }
        } catch (final Exception e) {
            // ignore
        }
    }
}
