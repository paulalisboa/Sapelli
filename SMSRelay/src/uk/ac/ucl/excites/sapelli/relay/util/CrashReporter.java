package uk.ac.ucl.excites.sapelli.relay.util;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;


/**
 * Simple Class to Log App Crashes to a file<br>
 * in order to use call: <br>
 * <code>Thread.setDefaultUncaughtExceptionHandler(new CrashReporter(localPath, getResources().getString(R.string.app_name)))</code>
 * 
 * @author Michalis Vitos
 * 
 */
public class CrashReporter implements UncaughtExceptionHandler
{

	private UncaughtExceptionHandler defaultUEH;
	private String localPath;
	private String namePrefix;

	public CrashReporter(String localPath, String namePrefix)
	{
		this.localPath = localPath;
		this.namePrefix = namePrefix;

		// Create the folder if does not exist
		FileHelpers.createFolder(localPath);
		this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
	}

	public void uncaughtException(Thread t, Throwable e)
	{
		String timestamp = String.valueOf(System.currentTimeMillis());
		final Writer result = new StringWriter();
		final PrintWriter printWriter = new PrintWriter(result);
		e.printStackTrace(printWriter);
		String stacktrace = result.toString();
		printWriter.close();
		String filename = namePrefix + "_" + timestamp + ".stacktrace";

		if(localPath != null)
		{
			writeToFile(stacktrace, filename);
		}

		defaultUEH.uncaughtException(t, e);
	}

	private void writeToFile(String stacktrace, String filename)
	{
		try
		{
			BufferedWriter bos = new BufferedWriter(new FileWriter(localPath + "/" + filename));
			bos.write(stacktrace);
			bos.flush();
			bos.close();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
}