package com.dataiku.wt1.storage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.dataiku.wt1.ProcessingQueue;
import com.dataiku.wt1.ProcessingQueue.Stats;
import com.dataiku.wt1.ConfigConstants;
import com.dataiku.wt1.TrackedRequest;
import com.dataiku.wt1.TrackingRequestProcessor;
import com.dataiku.wt1.Utils;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

/**
 * 
 */
public class FSStorageProcessor implements TrackingRequestProcessor {
    private ByteArrayOutputStream curBuf;
    private GZIPOutputStream curBufGZ;
    private int writtenBeforeGZ;
    private int writtenEvents;
    private long startDate;
    private String rootDir;
    private int newFileTriggerSize = 1024 * 1024;
    private int newFileTriggerInterval = 0;
    private CSVFormatWriter csvWriter;

    public static final String NEW_FILE_TRIGGER_SIZE_PARAM = "newFileTriggerSize";
    public static final String NEW_FILE_TRIGGER_INTERVAL_PARAM = "newFileTriggerInterval";
    public static final String ROOT_PARAM = "rootDir";

    private void initBuffer() throws IOException {
        curBuf = new ByteArrayOutputStream();
        curBufGZ = new GZIPOutputStream(curBuf);
        curBufGZ.write(csvWriter.makeHeaderLine().getBytes("utf8"));
        writtenEvents = 0;
        writtenBeforeGZ = 0;
        startDate = System.currentTimeMillis();
    }

    private synchronized void flushBuffer(boolean reinit) throws IOException {
    	if (curBuf == null) {
    		// processor has already been shutdown
    		return;
    	}
    	curBufGZ.flush();
    	curBufGZ.close();
    	
    	if (writtenEvents > 0) {
    		// No instance for FS storage
    		String name = CSVFormatWriter.newFileName(null);

    		File f = new File(new File(rootDir), name);
    		File dir = f.getParentFile();
    		dir.mkdirs();
    		File tmp = File.createTempFile("___", null, dir);
    		logger.info("Writing new file name=" + name + " (inSize=" + writtenBeforeGZ + " gzSize=" +  curBuf.size() + ")");
    		FileUtils.writeByteArrayToFile(tmp, curBuf.toByteArray());
    		tmp.renameTo(f);
    		logger.info("File written and flushed");

    		Stats stats = ProcessingQueue.getInstance().getStats();
    		synchronized (stats) {
    			stats.createdFiles++;
    			stats.savedEvents += writtenEvents;
    			stats.savedEventsGZippedSize += curBuf.size();
    			stats.savedEventsInputSize += writtenBeforeGZ;
    		}
    	} else {
    		logger.info("No events to flush");
    	}
    	
    	curBuf = null;
    	curBufGZ = null;
    	if (reinit) {
    		initBuffer();
    	}
    }

    @Override
    public void init(Map<String, String> params) throws IOException {
        rootDir = params.get(ROOT_PARAM);
        if (rootDir == null) {
    		logger.error("Missing configuration parameter " + ROOT_PARAM);
    		throw new IllegalArgumentException(ROOT_PARAM);
        }
        String fileSizeParam = params.get(NEW_FILE_TRIGGER_SIZE_PARAM);
        if (fileSizeParam != null) {
        	try {
        		newFileTriggerSize = Integer.parseInt(fileSizeParam);
        	} catch (NumberFormatException e) {
        		logger.error("Invalid value for configuration parameter " + NEW_FILE_TRIGGER_SIZE_PARAM);
        		throw e;
        	}
        }
        String fileIntervalParam = params.get(NEW_FILE_TRIGGER_INTERVAL_PARAM);
        if (fileIntervalParam != null) {
        	try {
        		newFileTriggerInterval = Integer.parseInt(fileIntervalParam);
        	} catch (NumberFormatException e) {
        		logger.error("Invalid value for configuration parameter " + NEW_FILE_TRIGGER_INTERVAL_PARAM);
        		throw e;   		
        	}
        }
        
        csvWriter = new CSVFormatWriter(
                Utils.parseCSVToSet(params.get(ConfigConstants.INLINED_VISITOR_PARAMS)),
                Utils.parseCSVToSet(params.get(ConfigConstants.INLINED_SESSION_PARAMS)),
                Utils.parseCSVToSet(params.get(ConfigConstants.INLINED_EVENT_PARAMS)));
        initBuffer();
    }

    @Override
    public void process(TrackedRequest req) throws IOException {
        if (logger.isTraceEnabled()) {
            logger.trace("Processing request, curFile=" + writtenBeforeGZ);
        }

        String line = csvWriter.makeLogLine(req);
        byte[] data = line.getBytes("utf8");
        writtenBeforeGZ += data.length;
        writtenEvents++;

        curBufGZ.write(data);
        if (logger.isTraceEnabled()) {
            logger.trace("Written " + writtenBeforeGZ + " -> " + curBuf.size());
        }

        if ((newFileTriggerSize > 0 && writtenBeforeGZ > newFileTriggerSize) ||
        	(newFileTriggerInterval > 0 && System.currentTimeMillis() > startDate + newFileTriggerInterval * 1000L)) {
        	flushBuffer(true);
        }
    }

    @Override
    public void shutdown() throws IOException {
        flushBuffer(false);
    }

	@Override
	public void flush() throws IOException {
		flushBuffer(true);
	}

	private static final Logger logger = Logger.getLogger("wt1.processor.fs");

    @Override
    public void service(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        throw new ServletException("No HTTP service for FSStorage");
    }

}