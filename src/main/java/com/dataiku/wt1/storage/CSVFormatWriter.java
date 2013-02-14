package com.dataiku.wt1.storage;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.dataiku.wt1.TrackedRequest;

public class CSVFormatWriter {
    /** Compute the filename to use for a new output file */
    public static String newFileName(String instance) {
        long now = System.currentTimeMillis();
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy/MM/dd/HH/mm-ss");
        String name = "tracker/" + sdf1.format(new Date(now)) + (instance == null ? null : "-" + instance) + ".log.gz";
        return name;
    }
    
    private static String escape(String in) {
        if (in == null) return "null";
        return in.replace("\"", "\\\"");
    }

    public static String makeHeaderLine() {
        return "#timestamp\tclient_addr\tvisitor_id\tlocation\treferer\tuser-agent\ttype\tvisitor_params\tsession_params\tevent_params\t" +
                "br_width\tbr_height\tsc_width\tsc_height\tbr_lang\ttz_off\n";
    }    

    /**
     * Write the line of log for one request
     */
    public static String makeLogLine(TrackedRequest req) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd-HH:mm:ss.SSS");
        StringBuilder sb = new StringBuilder();
        sb.append(sdf.format(new Date(req.now)));
        sb.append('\t');
        sb.append(req.origAddress);
        sb.append('\t');
        sb.append(req.visitorId);
        sb.append("\t\"");
        sb.append(escape(req.page));
        sb.append("\"\t\"");
        sb.append(escape(req.referer));
        sb.append("\"\t\"");
        sb.append(escape(req.ua));
        sb.append("\"\t\"");
        sb.append(escape(req.type));
        sb.append("\"\t\"");
        sb.append(escape(req.visitorParams));
        sb.append("\"\t\"");
        sb.append(escape(req.sessionParams));
        sb.append("\"\t\"");
        sb.append(escape(req.eventParams));
        sb.append("\"\t");
        sb.append(req.browserWidth);
        sb.append("\t");
        sb.append(req.browserHeight);
        sb.append("\t");
        sb.append(req.screenWidth);
        sb.append("\t");
        sb.append(req.screenHeight);
        sb.append("\t\"");
        sb.append(req.browserLanguage);
        sb.append("\"\t");
        sb.append(req.tzOffset);
        sb.append("\n");

        return sb.toString();
    }
}
