package com.dataiku.wt1.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.FlumeException;
import org.apache.flume.api.RpcClient;
import org.apache.flume.api.RpcClientFactory;
import org.apache.flume.event.EventBuilder;
import org.apache.log4j.Logger;

import com.dataiku.wt1.ConfigConstants;
import com.dataiku.wt1.ProcessingQueue;
import com.dataiku.wt1.TrackedRequest;
import com.dataiku.wt1.TrackingRequestProcessor;
import com.dataiku.wt1.Utils;
import com.dataiku.wt1.ProcessingQueue.Stats;


public class FlumeSourceProcessor implements TrackingRequestProcessor {

	private CSVFormatWriter csvWriter;
	private Properties flumeConfig;
	private RpcClient rpcClient;
	private long connectionDate;
	private int batchSize;
	private int flushInterval;
	private int maxBufferSize = 1000;
	private int reconnectDelay = 60;
	private boolean timestampHeader = false;
	private String hostHeader = null;
	private List<Event> events;
	private int writtenSize;
	private boolean shutdown = false;
	private long flushDate;

    public static final String FLUSH_INTERVAL_PARAM = "flushInterval";
	public static final String MAX_BUFFER_SIZE_PARAM = "maxBufferSize";
    public static final String RECONNECT_DELAY_PARAM = "reconnectDelay";
    public static final String TIMESTAMP_HEADER_PARAM = "timestampHeader";
    public static final String HOST_HEADER_PARAM = "hostHeader";

	@Override
	public void init(Map<String, String> params) throws IOException {

		csvWriter = new CSVFormatWriter(
				Utils.parseCSVToSet(params.get(ConfigConstants.INLINED_VISITOR_PARAMS)),
				Utils.parseCSVToSet(params.get(ConfigConstants.INLINED_SESSION_PARAMS)),
				Utils.parseCSVToSet(params.get(ConfigConstants.INLINED_EVENT_PARAMS)));

        String flushIntervalParam = params.get(FLUSH_INTERVAL_PARAM);
        if (flushIntervalParam != null) {
        	try {
        		flushInterval = Integer.parseInt(flushIntervalParam);
        	} catch (NumberFormatException e) {
        		logger.error("Invalid value for configuration parameter " + FLUSH_INTERVAL_PARAM);
        		throw e;
        	}
        }
        String maxBufferSizeParam = params.get(MAX_BUFFER_SIZE_PARAM);
        if (maxBufferSizeParam != null) {
        	try {
        		maxBufferSize = Integer.parseInt(maxBufferSizeParam);
        	} catch (NumberFormatException e) {
        		logger.error("Invalid value for configuration parameter " + MAX_BUFFER_SIZE_PARAM);
        		throw e;
        	}
        }
        String reconnectDelayParam = params.get(RECONNECT_DELAY_PARAM);
        if (reconnectDelayParam != null) {
        	try {
        		reconnectDelay = Integer.parseInt(reconnectDelayParam);
        	} catch (NumberFormatException e) {
        		logger.error("Invalid value for configuration parameter " + RECONNECT_DELAY_PARAM);
        		throw e;
        	}
        }
        timestampHeader = "true".equalsIgnoreCase(params.get(TIMESTAMP_HEADER_PARAM));
        hostHeader = params.get(HOST_HEADER_PARAM);

        flumeConfig = new Properties();
		for (String k : params.keySet()) {
			if (k.startsWith("flume.")) {
				flumeConfig.put(k.replace("flume.", ""), params.get(k));
			}
		}
		events = new ArrayList<Event>();
        flushDate = System.currentTimeMillis();
		logger.info("Connecting Flume source");
		connectionDate = System.currentTimeMillis();
		try {
			rpcClient = RpcClientFactory.getInstance(flumeConfig);
			batchSize = rpcClient.getBatchSize();
		} catch (FlumeException e) {
			logger.error("Error connecting flume source", e);
		}
	}
	
	private synchronized void flushBuffer(boolean reinit) throws IOException {
		if (shutdown) {
    		// processor has already been shutdown
    		return;
    	}
		if (events.size() > 0) {
			if (rpcClient == null || ! rpcClient.isActive()) {
				if (System.currentTimeMillis() > connectionDate + reconnectDelay * 1000) {
					logger.info("Reconnecting Flume source");
					connectionDate = System.currentTimeMillis(); 
					try {
						if (rpcClient != null) {
							rpcClient.close();
						}
						rpcClient = RpcClientFactory.getInstance(flumeConfig);
						batchSize = rpcClient.getBatchSize();
					} catch (FlumeException e) {
						logger.error("Error reconnecting flume source", e);
						throw new IOException("flume connection error");
					}
				} else {
					throw new IOException("flume connection is dead");
				}
			}
			
			try {
				logger.info("Sending event batch, size=" + events.size());
				rpcClient.appendBatch(events);
			} catch (EventDeliveryException e) {
				logger.error("Error sending flume events", e);
				rpcClient.close();
				throw new IOException("flume event delivery error");
			}

    		Stats stats = ProcessingQueue.getInstance().getStats();
    		synchronized (stats) {
    			stats.createdFiles++;
    			stats.savedEvents += events.size();
    			stats.savedEventsGZippedSize += writtenSize;
    			stats.savedEventsInputSize += writtenSize;
    		}
			
			events.clear();
			writtenSize = 0;
	        flushDate = System.currentTimeMillis();
		}
		if (! reinit) {
			if (rpcClient != null) {
				rpcClient.close();
			}
			shutdown = true;
		}
	}

	@Override
	public void process(TrackedRequest req) throws IOException {
        String line = csvWriter.makeLogLine(req);
        byte[] data = line.getBytes("utf8");

        if (events.size() >= maxBufferSize) {
        	flushBuffer(true);
        }
        Event event = EventBuilder.withBody(data);
        if (timestampHeader) {
        	event.getHeaders().put("timestamp", Long.toString(System.currentTimeMillis()));
        }
        if (hostHeader != null) {
        	event.getHeaders().put("host", hostHeader);
        }
        events.add(event);
        writtenSize += data.length;
        if (events.size() >= batchSize ||
        		(flushInterval > 0 && System.currentTimeMillis() > flushDate + flushInterval * 1000L)) {
        	try {
        		flushBuffer(true);
        	} catch (IOException e) {
        		// Catch error since event has been buffered
        	}
        }
	}

	@Override
	public void service(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, ServletException {
        throw new ServletException("No HTTP service for FSStorage");		
	}

	@Override
	public void shutdown() throws IOException {
		flushBuffer(false);
	}

	@Override
	public void flush() throws IOException {
		flushBuffer(true);
	}

	private static final Logger logger = Logger.getLogger("wt1.processor.flume");
}
