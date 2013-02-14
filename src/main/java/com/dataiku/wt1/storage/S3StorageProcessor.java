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
import com.dataiku.wt1.TrackedRequest;
import com.dataiku.wt1.TrackingRequestProcessor;

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

    private AmazonS3Client s3client;
    private String instanceId;
    private File tmpFolder;
    private String bucketName;
    private int newFileTriggerSize;

    public static final String NEW_FILE_TRIGGER_PARAM = "newFileTriggerSize";
    public static final String TMP_FOLDER_PARAM = "tmpFolder";
    public static final String BUCKET_PARAM = "bucketName";
    public static final String ACCESS_KEY_PARAM = "accessKey";
    public static final String SECRET_KEY_PARAM = "secretKey";
    public static final String INSTANCE_ID_PARAM = "instanceId";

    private void initBuffer() throws IOException {
        tmpFile = File.createTempFile("s3storage", null, tmpFolder);
        logger.info("Initializing temporary storage to " +tmpFile);
        
        fileOS = new FileOutputStream(tmpFile);
        gzOS = new GZIPOutputStream(fileOS);
        gzOS.write(CSVFormatWriter.makeHeaderLine().getBytes("utf8"));
        writtenBeforeGZ = 0;
        writtenEvents = 0;
    }

    private void flushBuffer() throws IOException {
        gzOS.flush();
        gzOS.close();
        fileOS.flush();
        fileOS.close();
       
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
        FileUtils.forceDelete(tmpFile);
        tmpFile = null;
        fileOS = null;
        gzOS = null;
    }

    @Override
    public void init(Map<String, String> params) throws IOException {
        this.bucketName = params.get(BUCKET_PARAM);
        this.newFileTriggerSize = Integer.parseInt(params.get(NEW_FILE_TRIGGER_PARAM));
        String tmpFolder = params.get(TMP_FOLDER_PARAM);
        if (tmpFolder != null) {
            this.tmpFolder = new File(tmpFolder);
            FileUtils.forceMkdir(this.tmpFolder);
        } else {
            this.tmpFolder = new File(System.getProperty("java.io.tmpdir"));
        }
        
        this.instanceId = params.get(INSTANCE_ID_PARAM);
        
        BasicAWSCredentials creds = new BasicAWSCredentials(params.get(ACCESS_KEY_PARAM), params.get(SECRET_KEY_PARAM));
        s3client = new AmazonS3Client(creds);
        
        initBuffer();
    }

    @Override
    public void process(TrackedRequest req) throws IOException {
        if (logger.isTraceEnabled()) {
            logger.trace("Processing request, curWritten=" + writtenBeforeGZ);
        }

        String line = CSVFormatWriter.makeLogLine(req);
        byte[] data = line.getBytes("utf8");
        writtenBeforeGZ += data.length;
        writtenEvents++;

        gzOS.write(data);

        if (logger.isTraceEnabled()) {
            logger.trace("Written " + writtenBeforeGZ);
        }

        if (writtenBeforeGZ > newFileTriggerSize) {
            flushBuffer();
            initBuffer();
        }
    }

    @Override
    public void shutdown() throws IOException {
        flushBuffer();
    }

    private static final Logger logger = Logger.getLogger("wt1.storage.s3");

    @Override
    public void service(HttpServletRequest req, HttpServletResponse resp)
            throws IOException, ServletException {
    }
}