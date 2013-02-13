package com.dataiku.wt1.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.zip.GZIPOutputStream;


import org.apache.log4j.Logger;

import com.dataiku.wt1.ProcessingQueue;
import com.dataiku.wt1.TrackedRequest;
import com.dataiku.wt1.TrackingRequestProcessor;
import com.dataiku.wt1.ProcessingQueue.Stats;
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
	private FileService fileService = FileServiceFactory.getFileService();

	private String bucketName;
	private int newFileTriggerSize;

	public static final String NEW_FILE_TRIGGER_PARAM = "newFileTriggerSize";
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
		curBufGZ.write(CSVFormatWriter.makeHeaderLine().getBytes("utf8"));
		writtenBeforeGZ = 0;
		writtenEvents = 0;
	}

	private void flushBuffer() throws IOException {
	    String name = CSVFormatWriter.newFileName(BackendServiceFactory.getBackendService().getCurrentInstance());

		logger.info("Opening new file name=" + name);
		AppEngineFile file = newFile(name);
		FileWriteChannel channel = fileService.openWriteChannel(file, true);
		
		curBufGZ.flush();
		curBufGZ.close();
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
				
		curBuf = null;
		curBufGZ = null;
	}

	@Override
	public void init(Map<String, String> params) throws IOException {
		this.bucketName = params.get(BUCKET_PARAM);
		this.newFileTriggerSize = Integer.parseInt(params.get(NEW_FILE_TRIGGER_PARAM));
		initBuffer();
	}

	@Override
	public void process(TrackedRequest req) throws IOException {
		if (logger.isTraceEnabled()) {
			logger.trace("Processing request, curFile=" + writtenBeforeGZ);
		}

		String line = CSVFormatWriter.makeLogLine(req);
		byte[] data = line.getBytes("utf8");
		writtenBeforeGZ += data.length;
		writtenEvents++;

		curBufGZ.write(data);
		
		if (logger.isTraceEnabled()) {
		    logger.trace("Written " + writtenBeforeGZ + " -> " + curBuf.size());
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

	private static final Logger logger = Logger.getLogger("wt1.processor.gae");
}