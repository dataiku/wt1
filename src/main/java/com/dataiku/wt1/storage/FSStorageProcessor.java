package com.dataiku.wt1.storage;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.zip.GZIPOutputStream;


import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import com.dataiku.wt1.ProcessingQueue;
import com.dataiku.wt1.TrackedRequest;
import com.dataiku.wt1.TrackingRequestProcessor;
import com.dataiku.wt1.ProcessingQueue.Stats;

/**
 * 
 */
public class FSStorageProcessor implements TrackingRequestProcessor{
    private ByteArrayOutputStream curBuf;
    private GZIPOutputStream curBufGZ;
    private int writtenBeforeGZ;
    private int writtenEvents;
    private String rootDir;
    private int newFileTriggerSize;

    public static final String NEW_FILE_TRIGGER_PARAM = "newFileTriggerSize";
    public static final String ROOT_PARAM = "rootDir";

    private void initBuffer() throws IOException {
        curBuf = new ByteArrayOutputStream();
        curBufGZ = new GZIPOutputStream(curBuf);
        curBufGZ.write(CSVFormatWriter.makeHeaderLine().getBytes("utf8"));
        writtenEvents = 0;
        writtenBeforeGZ = 0;
    }

    private void flushBuffer() throws IOException {
        // No instance name on FS storage
        String name = CSVFormatWriter.newFileName(null);

        File f = new File(new File(rootDir), name);
        f.getParentFile().mkdirs();
        curBufGZ.flush();
        curBufGZ.close();
        logger.info("Writing new file name=" + name + " inSize=" + writtenBeforeGZ + " gzSize=" +  curBuf.size() + ")");
        FileUtils.writeByteArrayToFile(f, curBuf.toByteArray());
        logger.info("File written and flushed");
        
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
        this.rootDir = params.get(ROOT_PARAM);
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

        if (writtenBeforeGZ > newFileTriggerSize){
            flushBuffer();
            initBuffer();
        }
    }

    @Override
    public void shutdown() throws IOException {
        flushBuffer();
    }

    private static final Logger logger = Logger.getLogger("wt1.processor.fs");
}