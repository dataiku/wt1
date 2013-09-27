package com.dataiku.wt1.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.dataiku.wt1.ProcessingQueue;
import com.dataiku.wt1.ProcessingQueue.Stats;
import com.dataiku.wt1.ConfigConstants;
import com.dataiku.wt1.TrackedRequest;
import com.dataiku.wt1.TrackingRequestProcessor;
import com.dataiku.wt1.Utils;

/**
 * S3 implementation of the storage engine.
 * 
 * The data is not streamed but written in a temporary file and then sent at once to S3 because
 * S3 is not designed for long writes.
 */
public class S3StorageProcessor implements TrackingRequestProcessor{
    private File tmpFile;
    private FileOutputStream fileOS;
    private GZIPOutputStream gzOS;
    private int writtenBeforeGZ;
    private int writtenEvents;
    private long startDate;

    private AmazonS3Client s3client;
    private String instanceId;
    private File tmpFolder;
    private String bucketName;
    private int newFileTriggerSize = 1024 * 1024;
    private int newFileTriggerInterval = 0;
    private CSVFormatWriter csvWriter;

    public static final String NEW_FILE_TRIGGER_SIZE_PARAM = "newFileTriggerSize";
    public static final String NEW_FILE_TRIGGER_INTERVAL_PARAM = "newFileTriggerInterval";
    public static final String TMP_FOLDER_PARAM = "tmpFolder";
    public static final String BUCKET_PARAM = "bucketName";
    public static final String ACCESS_KEY_PARAM = "accessKey";
    public static final String SECRET_KEY_PARAM = "secretKey";
    public static final String INSTANCE_ID_PARAM = "instanceId";

    private void initBuffer() throws IOException {
        tmpFile = File.createTempFile("s3storage", null, tmpFolder);
        logger.info("Initializing temporary storage to " + tmpFile);
        
        fileOS = new FileOutputStream(tmpFile);
        gzOS = new GZIPOutputStream(fileOS);
        gzOS.write(csvWriter.makeHeaderLine().getBytes("utf8"));
        writtenBeforeGZ = 0;
        writtenEvents = 0;
        startDate = System.currentTimeMillis();
    }

    private synchronized void flushBuffer(boolean reinit) throws IOException {
    	if (fileOS == null) {
    		// processor has already been shutdown
    		return;
    	}
    	gzOS.flush();
    	gzOS.close();
    	fileOS.flush();
    	fileOS.close();

    	if (writtenEvents > 0) {
    		String name = CSVFormatWriter.newFileName(instanceId);
    		logger.info("Will write to S3, file=" + name + ", size=" + tmpFile.length());
    		s3client.putObject(bucketName, name, tmpFile);
    		logger.info("File written");

    		Stats stats = ProcessingQueue.getInstance().getStats();
    		synchronized (stats) {
    			stats.createdFiles++;
    			stats.savedEvents += writtenEvents;
    			stats.savedEventsGZippedSize += tmpFile.length();
    			stats.savedEventsInputSize += writtenBeforeGZ;
    		}
    	} else {
    		logger.info("No events to flush");
    	}

    	FileUtils.forceDelete(tmpFile);
    	tmpFile = null;
    	fileOS = null;
    	gzOS = null;

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
        String tmpFolderParam = params.get(TMP_FOLDER_PARAM);
        if (tmpFolderParam != null) {
            tmpFolder = new File(tmpFolderParam);
            FileUtils.forceMkdir(tmpFolder);
        } else {
            tmpFolder = new File(System.getProperty("java.io.tmpdir"));
        }
        
        instanceId = params.get(INSTANCE_ID_PARAM);
        
        BasicAWSCredentials creds = new BasicAWSCredentials(params.get(ACCESS_KEY_PARAM), params.get(SECRET_KEY_PARAM));
        s3client = new AmazonS3Client(creds);
        
        csvWriter = new CSVFormatWriter(
        		Utils.parseCSVToSet(params.get(ConfigConstants.INLINED_VISITOR_PARAMS)),
        		Utils.parseCSVToSet(params.get(ConfigConstants.INLINED_SESSION_PARAMS)),
        		Utils.parseCSVToSet(params.get(ConfigConstants.INLINED_EVENT_PARAMS)));
        
        
        initBuffer();
    }

    @Override
    public void process(TrackedRequest req) throws IOException {
        if (logger.isTraceEnabled()) {
            logger.trace("Processing request, curWritten=" + writtenBeforeGZ);
        }

        String line = csvWriter.makeLogLine(req);
        byte[] data = line.getBytes("utf8");
        writtenBeforeGZ += data.length;
        writtenEvents++;

        gzOS.write(data);

        if (logger.isTraceEnabled()) {
            logger.trace("Written " + writtenBeforeGZ);
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

    private static final Logger logger = Logger.getLogger("wt1.storage.s3");

    @Override
    public void service(HttpServletRequest req, HttpServletResponse resp)
            throws IOException, ServletException {
    }
}