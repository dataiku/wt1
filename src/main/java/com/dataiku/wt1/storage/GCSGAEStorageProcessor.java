package com.dataiku.wt1.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import com.dataiku.wt1.ConfigConstants;
import com.dataiku.wt1.ProcessingQueue;
import com.dataiku.wt1.ProcessingQueue.Stats;
import com.dataiku.wt1.TrackedRequest;
import com.dataiku.wt1.TrackingRequestProcessor;
import com.dataiku.wt1.Utils;
import com.google.appengine.api.backends.BackendServiceFactory;
import com.google.appengine.api.files.AppEngineFile;
import com.google.appengine.api.files.FileService;
import com.google.appengine.api.files.FileServiceFactory;
import com.google.appengine.api.files.FileWriteChannel;
import com.google.appengine.api.files.GSFileOptions.GSFileOptionsBuilder;

/**
 * Google App Engine implementation of the tracked request processor.
 * It stores data in the Google Cloud Storage.
 * 
 * We need to write the file at once, and buffer everything that comes before, because
 * you cannot hold on a Google Cloud Storage file handle for more than 30 seconds.
 */
public class GCSGAEStorageProcessor implements TrackingRequestProcessor{
	private ByteArrayOutputStream curBuf;
	private GZIPOutputStream curBufGZ;
	private int writtenBeforeGZ;
	private int writtenEvents;
    private long startDate;
	private FileService fileService = FileServiceFactory.getFileService();

	private String bucketName;
    private int newFileTriggerSize = 1024 * 1024;
    private int newFileTriggerInterval = 0;
	private CSVFormatWriter csvWriter;

	public static final String NEW_FILE_TRIGGER_SIZE_PARAM = "newFileTriggerSize";
    public static final String NEW_FILE_TRIGGER_INTERVAL_PARAM = "newFileTriggerInterval";
	public static final String BUCKET_PARAM = "bucketName";

	/**
	 * Create a new file on Cloud storage
	 */
	private AppEngineFile newFile(String name) throws IOException {
		GSFileOptionsBuilder optionsBuilder = new GSFileOptionsBuilder()
		.setBucket(bucketName)
		.setKey(name);
		return fileService.createNewGSFile(optionsBuilder.build());
	}

	private void initBuffer() throws IOException {
		curBuf = new ByteArrayOutputStream();
		curBufGZ = new GZIPOutputStream(curBuf);
		curBufGZ.write(csvWriter.makeHeaderLine().getBytes("utf8"));
		writtenBeforeGZ = 0;
		writtenEvents = 0;
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
			String name = CSVFormatWriter.newFileName("b" + BackendServiceFactory.getBackendService().getCurrentInstance());

			logger.info("Opening new file name=" + name);
			AppEngineFile file = newFile(name);
			FileWriteChannel channel = fileService.openWriteChannel(file, true);

			logger.info("Writing new file name=" + name + " inSize=" + writtenBeforeGZ + " gzSize=" +  curBuf.size() + ")");
			channel.write(ByteBuffer.wrap(curBuf.toByteArray()));
			logger.info("Closing new file");
			channel.closeFinally();
			logger.info("Closed new file");

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
		bucketName = params.get(BUCKET_PARAM);
        if (bucketName == null) {
    		logger.error("Missing configuration parameter " + BUCKET_PARAM);
    		throw new IllegalArgumentException(BUCKET_PARAM);
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

		String line = csvWriter.makeLogLine(req, true);
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

	@Override
    public void service(HttpServletRequest req, HttpServletResponse resp)
            throws IOException, ServletException {
    }

	private static final Logger logger = Logger.getLogger("wt1.processor.gae");
}