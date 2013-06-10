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
public class FSStorageProcessor implements TrackingRequestProcessor{
    private ByteArrayOutputStream curBuf;
    private GZIPOutputStream curBufGZ;
    private int writtenBeforeGZ;
    private int writtenEvents;
    private String rootDir;
    private int newFileTriggerSize;
    private CSVFormatWriter csvWriter;

    public static final String NEW_FILE_TRIGGER_PARAM = "newFileTriggerSize";
    public static final String ROOT_PARAM = "rootDir";

    private void initBuffer() throws IOException {
        curBuf = new ByteArrayOutputStream();
        curBufGZ = new GZIPOutputStream(curBuf);
        curBufGZ.write(csvWriter.makeHeaderLine().getBytes("utf8"));
        writtenEvents = 0;
        writtenBeforeGZ = 0;
    }

    private void flushBuffer() throws IOException {
        // No instance for FS storage
        String name = CSVFormatWriter.newFileName(null);

        File f = new File(new File(rootDir), name);
        f.getParentFile().mkdirs();
        curBufGZ.flush();
        curBufGZ.close();
        logger.info("Writing new file name=" + name + " (inSize=" + writtenBeforeGZ + " gzSize=" +  curBuf.size() + ")");
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
        
        this.csvWriter = new CSVFormatWriter(
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

    @Override
    public void service(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        throw new ServletException("No HTTP service for FSStorage");
    }
}