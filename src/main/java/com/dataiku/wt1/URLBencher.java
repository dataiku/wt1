package com.dataiku.wt1;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;

public class URLBencher {
	static HttpClient client;
	
	static class MyThread extends Thread {
		int threadId;
		URL u;
		int nb;
		public void run() {
			long totalHTime = 0, maxHTime = 0;
			long totalBTime = 0, maxBTime = 0;
			for (int i = 0; i < nb; i++) {
				try {
					long before = System.nanoTime();
					/*
					HttpURLConnection conn = (HttpURLConnection) u.openConnection();
					int code = conn.getResponseCode();
					*/
					GetMethod gm = new GetMethod(u.toExternalForm());
					int code = client.executeMethod(gm);

					long atCode = System.nanoTime();

					InputStream is = gm.getResponseBodyAsStream();
					IOUtils.toByteArray(is);
					is.close();
					long atEnd = System.nanoTime();
					System.out.println("t=" + threadId + " i=" + i + " c=" + code + " head=" + (atCode-before)/1000 + " body=" + (atEnd-atCode)/1000);

					totalHTime += (atCode-before)/1000;
					totalBTime += (atEnd-atCode)/1000;
					maxHTime = Math.max(maxHTime, (atCode-before)/1000);
					maxBTime = Math.max(maxBTime, (atEnd-atCode)/1000);
					
				} catch (IOException e) {
					System.out.println("t= "+ threadId + " i=" + i + " FAIL " + e.getMessage());
				}
			}
			System.out.println("t= "+ threadId + " avgHead=" + totalHTime/nb + " avgBody=" + totalBTime/nb + " maxHead=" + maxHTime + " maxBody=" + maxBTime);
		}
	}

	public static void main(String[] args) throws Exception {
		String url = args[0];
		URL u = new URL(url);

		int nb = Integer.parseInt(args[1]);
		int threads = Integer.parseInt(args[2]);
		
		client = new HttpClient();
		MultiThreadedHttpConnectionManager m = new MultiThreadedHttpConnectionManager();
		m.setMaxConnectionsPerHost(1000);
		m.setMaxTotalConnections(1000);
		client.setHttpConnectionManager(m);

		List<MyThread> tlist = new ArrayList<URLBencher.MyThread>();
		for (int i = 0; i < threads; i++) {
			MyThread mt = new MyThread();
			mt.u = u;
			mt.threadId = i;
			mt.nb = nb;
			mt.start();
			tlist.add(mt);
		}
		for (MyThread mt : tlist) {
			mt.join();
		}
	}
}

