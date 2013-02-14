package com.dataiku.wt1.tools;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.params.CookiePolicy;

public class URLBencher {
	static HttpClient client;
	
	static class MyThread extends Thread {
		int threadId;
		URL u;
		int nb;
		Random r = new Random();
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
					
					String url  = u.toExternalForm();
					if (r.nextInt(300) == 1) {
					    url += "?type=purchase&amount=" + r.nextInt(120);
					}

					GetMethod gm = new GetMethod(url);

					
					/* For some users, simulate the fact that they are the same, coming back (to have more visits than visitors) */
					if (r.nextInt(20 + r.nextInt(10)) == 1) {
					    gm.addRequestHeader("Cookie", "__wt1vic=888888888");
					}
					
//					System.out.println(url);
					gm.getParams().setCookiePolicy(CookiePolicy.IGNORE_COOKIES);
					int code = client.executeMethod(gm);

					long atCode = System.nanoTime();

					InputStream is = gm.getResponseBodyAsStream();
					IOUtils.toByteArray(is);
					is.close();
					long atEnd = System.nanoTime();
					if (i % 50 == 0) {
					    System.out.println("t=" + threadId + " i=" + i + " c=" + code + " head=" + (atCode-before)/1000 + " body=" + (atEnd-atCode)/1000);
					}

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

