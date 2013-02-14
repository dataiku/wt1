//package com.dataiku.wt1.tools;
//
//import java.io.IOException;
//import java.io.InputStream;
//import java.net.URL;
//import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.Semaphore;
//
//import org.apache.commons.httpclient.HttpClient;
//import org.apache.commons.httpclient.methods.GetMethod;
//import org.apache.commons.io.IOUtils;
//import org.apache.http.HttpResponse;
//import org.apache.http.client.methods.HttpGet;
//import org.apache.http.concurrent.FutureCallback;
//import org.apache.http.impl.nio.client.DefaultHttpAsyncClient;
//import org.apache.http.impl.nio.conn.PoolingClientAsyncConnectionManager;
//import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
//import org.apache.http.impl.nio.reactor.IOReactorConfig;
//import org.apache.http.nio.client.HttpAsyncClient;
//import org.apache.http.params.CoreConnectionPNames;
//import org.apache.http.protocol.BasicHttpContext;
//
//public class AsyncURLBencher {
//	static HttpClient client;
//	
//	static class MyThread extends Thread {
//		int threadId;
//		URL u;
//		int nb;
//		public void run() {
//			long totalHTime = 0, maxHTime = 0;
//			long totalBTime = 0, maxBTime = 0;
//			for (int i = 0; i < nb; i++) {
//				try {
//					long before = System.nanoTime();
//					/*
//					HttpURLConnection conn = (HttpURLConnection) u.openConnection();
//					int code = conn.getResponseCode();
//					*/
//					GetMethod gm = new GetMethod(u.toExternalForm());
//					int code = client.executeMethod(gm);
//
//					long atCode = System.nanoTime();
//
//					InputStream is = gm.getResponseBodyAsStream();
//					IOUtils.toByteArray(is);
//					is.close();
//					long atEnd = System.nanoTime();
//					System.out.println("t=" + threadId + " i=" + i + " c=" + code + " head=" + (atCode-before)/1000 + " body=" + (atEnd-atCode)/1000);
//
//					totalHTime += (atCode-before)/1000;
//					totalBTime += (atEnd-atCode)/1000;
//					maxHTime = Math.max(maxHTime, (atCode-before)/1000);
//					maxBTime = Math.max(maxBTime, (atEnd-atCode)/1000);
//					
//				} catch (IOException e) {
//					System.out.println("t= "+ threadId + " i=" + i + " FAIL " + e.getMessage());
//				}
//			}
//			System.out.println("t= "+ threadId + " avgHead=" + totalHTime/nb + " avgBody=" + totalBTime/nb + " maxHead=" + maxHTime + " maxBody=" + maxBTime);
//		}
//	}
//
//	public static void main(String[] args) throws Exception {
//		String url = args[0];
//		URL u = new URL(url);
//		
//		IOReactorConfig reactorConfig = new IOReactorConfig();
//		reactorConfig.setSoReuseAddress(true);
//		reactorConfig.setIoThreadCount(10);
//		
//		PoolingClientAsyncConnectionManager mgr = new PoolingClientAsyncConnectionManager(new DefaultConnectingIOReactor(reactorConfig));
//		mgr.setDefaultMaxPerRoute(100);
//		mgr.setMaxTotal(100);
//		
//		HttpAsyncClient aclient = new DefaultHttpAsyncClient(mgr);
//		  aclient.getParams()
//          .setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 30000)
//          .setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, 30000)
//          .setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
//          .setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true);
//
//		aclient.start();
//		
//		int totalNB = Integer.parseInt(args[1]);
//		int maxConcurrent = Integer.parseInt(args[2]);
//
//		final CountDownLatch latch = new CountDownLatch(totalNB);
//		final Semaphore s = new Semaphore(maxConcurrent);
//		
//		for (int i = 0; i < totalNB; i++) {
//		    final int ii = i;
//		    s.acquire();
//		    final long beg = System.currentTimeMillis(); 
//
//		    final HttpGet get = new HttpGet(u.toExternalForm());
//		    aclient.execute(get,  new BasicHttpContext(), new FutureCallback<HttpResponse>() {
//		        public void completed(final HttpResponse response) {
//                    latch.countDown();
//                    s.release();
//                    final long end = System.currentTimeMillis();
//                    if (ii% 50  == 0) {
//                        System.out.println("th=" + Thread.currentThread().getId() + " r= " + ii + " s=" + response.getStatusLine().getStatusCode() + " t=" + (end-beg));
//                    }
//                }
//
//                public void failed(final Exception ex) {
//                    latch.countDown();
//                    s.release();
//                    System.out.println(get.getRequestLine() + "->" + ex);
//                }
//
//                public void cancelled() {
//                    latch.countDown();
//                    s.release();
//                    System.out.println(get.getRequestLine() + " Ccancelled");
//                }
//		    });
//		}
////		latch.await();
//	}
//}
//
