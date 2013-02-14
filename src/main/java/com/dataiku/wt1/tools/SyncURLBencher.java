package com.dataiku.wt1.tools;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.SingleClientConnManager;
import org.apache.http.params.CoreConnectionPNames;

public class SyncURLBencher {
	
	static class MyThread extends Thread {
		int threadId;
		URL u;
		int nb;
		public void run() {
		    ClientConnectionManager cm = new SingleClientConnManager();
		    DefaultHttpClient httpclient = new DefaultHttpClient(cm);
		    httpclient.getParams()
	          .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 30000)
	          .setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 30000)
	          .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
	          .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true);

			long totalHTime = 0, maxHTime = 0;
			long totalBTime = 0, maxBTime = 0;
			for (int i = 0; i < nb; i++) {
				try {
					long before = System.nanoTime();
					HttpGet get = new HttpGet(u.toExternalForm());
					
					HttpResponse resp = httpclient.execute(get);
					
					int code = resp.getStatusLine().getStatusCode();

					long atCode = System.nanoTime();

					InputStream is = resp.getEntity().getContent();
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
		

		List<MyThread> tlist = new ArrayList<SyncURLBencher.MyThread>();
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

