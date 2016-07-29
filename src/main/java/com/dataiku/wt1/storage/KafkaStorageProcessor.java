package com.dataiku.wt1.storage;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.log4j.Logger;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONObject;

import com.amazonaws.util.json.JSONArray;
import com.dataiku.wt1.ProcessingQueue;
import com.dataiku.wt1.ProcessingQueue.Stats;
import com.dataiku.wt1.TrackedRequest;
import com.dataiku.wt1.TrackingRequestProcessor;
import com.dataiku.wt1.Utils;


public class KafkaStorageProcessor implements TrackingRequestProcessor {

    private Properties kafkaConfig = new Properties();
    private Producer<Void, String> strProducer;
    private String topic;
    private boolean decodeParams;

    @Override
    public void init(Map<String, String> params) throws IOException {
        this.topic = params.get("topic");
        this.decodeParams = params.containsKey("decodeParams") && "true".equals(params.get("decodeParams"));

        String kafkaServers = params.get("brokers");
        String acks = params.get("acks");
        if (acks == null) acks="0";
        String retries = params.get("retries");
        if (retries == null) retries="0";
        String linger = params.get("linger");
        if (linger == null) linger="1";

        String batch = params.get("batch.size");
        /* Each object tends to be fairly big, so use 16KB batches */
        if (batch == null) batch = "16384";
        String buffer = params.get("buffer.memory");
        if (buffer == null) buffer="4194304";

        kafkaConfig.put("bootstrap.servers", kafkaServers);
        kafkaConfig.put("acks", acks);
        kafkaConfig.put("retries", retries);
        kafkaConfig.put("batch.size", batch);
        kafkaConfig.put("linger.ms", linger);
        kafkaConfig.put("buffer.memory", buffer);
        kafkaConfig.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        kafkaConfig.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        logger.info("Starting Kafka storage with properties:");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        kafkaConfig.list(pw);
        pw.flush();
        logger.info(sw.toString());
        strProducer = new KafkaProducer<Void, String>(kafkaConfig);
    }

    public static final String ISO_FORMAT_LOCAL = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    public static String isoFormatUTC(long ts) {
        return ISODateTimeFormat.dateHourMinuteSecondMillis().withZone(DateTimeZone.UTC).print(ts) + "Z";
    }

    public static String isoFormatLocal(long ts) {
        return DateTimeFormat.forPattern(ISO_FORMAT_LOCAL).withZone(DateTimeZone.getDefault()).print(ts);
    }

    private JSONObject decodeParams(String params) {
        Map<String, String[]> eparams =  Utils.decodeQueryString(params);
        JSONObject paramsObj = new JSONObject();
        for (String key : eparams.keySet()) {
            String[] vals = eparams.get(key);
            if (vals.length == 1) {
                try {
                    paramsObj.put(key, Long.parseLong(vals[0]));   
                } catch (NumberFormatException e) {
                    try {
                        paramsObj.put(key, Double.parseDouble(vals[0]));
                    } catch (NumberFormatException e2) {
                        paramsObj.put(key, vals[0]);
                    }
                }
            } else{
                JSONArray arr = new JSONArray();
                for (String s : vals) arr.put(s);
                paramsObj.put(key, arr);
            }
        }
        return paramsObj;
    }

    @Override
    public void process(TrackedRequest req) throws IOException {
        JSONObject obj = new JSONObject();
        obj.put("visitorId", req.visitorId);
        obj.put("sessionId", req.sessionId);
        obj.put("origAddress", req.origAddress);
        obj.put("type", req.type);
        obj.put("serverTS", isoFormatLocal(req.serverTS));
        obj.put("clientTS", isoFormatLocal(req.clientTS));

        if (decodeParams) {
            obj.put("visitorParams", decodeParams(req.visitorParams));
            obj.put("sessionParams", decodeParams(req.sessionParams));
            obj.put("eventParams", decodeParams(req.eventParams));
        } else {
            if (req.visitorParams != null) obj.put("visitorParams", req.visitorParams);
            if (req.sessionParams != null) obj.put("sessionParams", req.sessionParams);
            if (req.eventParams != null) obj.put("eventParams", req.eventParams);
        }
        if (req.globalVisitorId != null) obj.put("globalVisitorId", req.globalVisitorId);

        if (req.page != null) obj.put("page", req.page);
        if (req.referer != null) obj.put("referer", req.referer);
        if (req.tzOffset != null) obj.put("tzOffset", req.tzOffset);
        if (req.browserWidth > 0) obj.put("browserWidth", req.browserWidth);
        if (req.browserHeight > 0) obj.put("browserHeight", req.browserHeight);
        if (req.screenWidth > 0 ) obj.put("screenWidth", req.screenWidth);
        if (req.screenHeight > 0) obj.put("screenHeight", req.screenHeight);
        if (req.ua != null) obj.put("ua", req.ua);
        if (req.browserLanguage != null) obj.put("browserLanguage", req.browserLanguage);

        //logger.info("Sending to Kafka: " + obj.toString());
        strProducer.send(new ProducerRecord<Void, String>(this.topic, obj.toString()));
        //logger.info("Sent: " + obj.toString());

        Stats stats = ProcessingQueue.getInstance().getStats();
        synchronized (stats) {
            stats.savedEvents ++;
        }
    }

    @Override
    public void service(HttpServletRequest req, HttpServletResponse resp)
            throws IOException, ServletException {
        throw new ServletException("No HTTP service for Kafka Storage");		
    }

    @Override
    public void shutdown() throws IOException {
        logger.info("Closing Kafka storage");
        strProducer.flush();
        logger.info("Close-Flushed");
        strProducer.close();
        logger.info("Closed");
    }

    @Override
    public void flush() throws IOException {
        logger.info("Flushing Kafka storage");
        strProducer.flush();
        logger.info("Flushed Kafka storage");
    }

    private static final Logger logger = Logger.getLogger("wt1.processor.kafka");
}
