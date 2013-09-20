package com.dataiku.wt1.gae;

import java.io.IOException;
import java.nio.channels.Channels;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.backends.BackendServiceFactory;
import com.google.appengine.api.files.AppEngineFile;
import com.google.appengine.api.files.FileService;
import com.google.appengine.api.files.FileServiceFactory;
import com.google.appengine.api.files.FileWriteChannel;
import com.google.appengine.api.files.GSFileOptions.GSFileOptionsBuilder;

/**
 * Google App Engine implementation of the tracked request processor.
 * It stores data in the Google Cloud Storage.
 */
public class TestServlet extends HttpServlet {
	
	private static final long serialVersionUID = 1L;

	private String newFileName() {
		long now = System.currentTimeMillis();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd/HH/mm-ss");
		String name = "trackerz/" + 
		BackendServiceFactory.getBackendService().getCurrentBackend() + "-" +
		BackendServiceFactory.getBackendService().getCurrentInstance() + "/" +
		sdf.format(new Date(now)) + ".log";
		return name;
	}
	
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		try {
		System.out.println(newFileName());
		 FileService fileService = FileServiceFactory.getFileService();
		GSFileOptionsBuilder optionsBuilder = new GSFileOptionsBuilder()
		.setBucket("wt1-test1")
		.setKey(newFileName());

		AppEngineFile aef = fileService.createNewGSFile(optionsBuilder.build());
		
		FileWriteChannel ch = fileService.openWriteChannel(aef, true);
		
		Channels.newWriter(ch, "utf8").append("pouet").flush();
		
		ch.closeFinally();
		System.out.println("SUCCESS **************");
		} catch (Exception e) {
			System.out.println("FAILURE **************************");
			e.printStackTrace();
		}
	}

}