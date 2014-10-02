/**
 * Sapelli data collection platform: http://sapelli.org
 * 
 * Copyright 2012-2014 University College London - ExCiteS group
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package uk.ac.ucl.excites.sapelli.collector;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import uk.ac.ucl.excites.sapelli.collector.db.PrefProjectStore;
import uk.ac.ucl.excites.sapelli.collector.db.ProjectStore;
import uk.ac.ucl.excites.sapelli.collector.db.sql.sqlite.android.AndroidSQLiteRecordStore;
import uk.ac.ucl.excites.sapelli.collector.util.CrashReporter;
import uk.ac.ucl.excites.sapelli.shared.db.DBException;
import uk.ac.ucl.excites.sapelli.shared.db.Store;
import uk.ac.ucl.excites.sapelli.shared.db.StoreClient;
import uk.ac.ucl.excites.sapelli.shared.io.FileHelpers;
import uk.ac.ucl.excites.sapelli.storage.db.RecordStore;
import uk.ac.ucl.excites.sapelli.util.Debug;
import android.app.Application;
import android.content.res.Configuration;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.support.v4.os.EnvironmentCompat;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

/**
 * Application App to keep the db4o object throughout the life-cycle of the Collector
 * 
 * @author Michalis Vitos, mstevens
 * 
 */
public class CollectorApp extends Application implements StoreClient
{

	// STATICS------------------------------------------------------------
	static private final String TAG = "CollectorApp";
	
	static private final String SAPELLI_FOLDER = "Sapelli" + File.separatorChar;
	static private final String DATABASE_BASENAME = "Sapelli";
	static private final String DEMO_PREFIX = "Demo_";
	
	//static private final boolean USE_PREFS_FOR_PROJECT_STORAGE = true;
	
	static private final String PROJECT_FOLDER = "Projects" + File.separator;
	static private final String TEMP_FOLDER = "Temp" + File.separator;
	static private final String DOWNLOAD_FOLDER = "Downloads" + File.separator;
	static private final String DUMP_FOLDER = "Dumps" + File.separator;
	static private final String EXPORT_FOLDER = "Export" + File.separator;
	
	static private final String CRASHLYTICS_VERSION_INFO = "VERSION_INFO";
	static private final String CRASHLYTICS_BUILD_INFO = "BUILD_INFO";
	static public final String CRASHLYTICS_DEVICE_ID_CRC32 = "SAPELLI_DEVICE_ID_CRC32";
	static public final String CRASHLYTICS_DEVICE_ID_MD5 = "SAPELLI_DEVICE_ID_MD5";

	/**
	 * Returns a prefix to be used on storage identifiers (DB4O filenames, SharedPref's names, etc.) when in demo mode
	 * (if not in demo mode the prefix is empty).
	 * The goal is to separate demo-mode storage from non-demo-mode installations and previous demo installations.
	 * 
	 * @return
	 */
	static public String getDemoPrefix()
	{
		return (BuildInfo.DEMO_BUILD ? DEMO_PREFIX + FileHelpers.makeValidFileName(BuildInfo.TIMESTAMP) : "");
	}
	
	// DYNAMICS-----------------------------------------------------------
	private File sapelliFolder;
	
	private ProjectStore projectStore = null;
	private RecordStore recordStore = null;
	//private TransmissionStore transmissionStore = null; 
	private Map<Store, Set<StoreClient>> storeClients;

	@Override
	public void onCreate()
	{
		super.onCreate();
		Debug.d("CollectorApp started.\nBuild info:\n" + BuildInfo.getAllInfo());

		// Start Crashlytics for bugs reporting
		if(!BuildConfig.DEBUG)
		{
			Crashlytics.start(this);
			Crashlytics.setString(CRASHLYTICS_VERSION_INFO, BuildInfo.getVersionInfo());
			Crashlytics.setString(CRASHLYTICS_BUILD_INFO, BuildInfo.getBuildInfo());
		}
	
		// Store clients:
		storeClients = new HashMap<Store, Set<StoreClient>>();
		
		// Set up a CrashReporter to the Sapelli/crash Folder
		try
		{
			Thread.setDefaultUncaughtExceptionHandler(new CrashReporter(getDumpFolderPath(), getResources().getString(R.string.app_name)));
		}
		catch(Exception e)
		{
			Log.e(TAG, "Could not set-up DefaultUncaughtExceptionHandler", e);
		}
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		// Debug.d(newConfig.toString());
	}

	/**
	 * @return finds/creates the Sapelli folder on the filesystem, and returns it as a File object
	 */
	public File getSapelliFolder()
	{
		if(sapelliFolder == null)
		{
			// Using application-specific folder (will be removed upon app uninstall!):
			File[] paths = ContextCompat.getExternalFilesDirs(this, null);
			if(paths != null)
			{
				// We count backwards because we prefer secondary external storage (which is likely to be on an SD card rather unremovable memory)
				for(int p = paths.length - 1; p >= 0; p--)
					if(Environment.MEDIA_MOUNTED.equals(EnvironmentCompat.getStorageState(paths[p])))
					{
						sapelliFolder = paths[p];
						break;
					}
			}
			// Fall-back: try to get write access to external storage (preferably SD card) root and create Sapelli folder if it doesn't exist yet:
			if(sapelliFolder == null)
			{
				File extStorageRoot = null;
				
				/* Old way: Using Environment2 library to get the path of the actual SD card if there is one,
				 * 			if not it gets the path of the emulated SD card/internal mass storage */
				//if(Environment.MEDIA_MOUNTED.equals(Environment2.getCardState()))
				//	cardDirectory = Environment2.getCardDirectory();
				
				// Using proper API (will not always return the real SD card path):
				if(Environment.MEDIA_MOUNTED.equals(EnvironmentCompat.getStorageState(Environment.getExternalStorageDirectory())))
					extStorageRoot = Environment.getExternalStorageDirectory();
				
				// Check if writable:
				if(!FileHelpers.isReadableWritableDirectory(extStorageRoot))
					throw new IllegalStateException("SD card or (emulated) external storage is not accessible");
				
				// Make Sapelli folder:
				sapelliFolder = new File(extStorageRoot.getAbsolutePath() + File.separator + SAPELLI_FOLDER);
				if(!sapelliFolder.exists())
				{
					if(!sapelliFolder.mkdirs())
						throw new IllegalStateException("Cannot create Sapelli folder");
				}
			}
		}
		
		// Return folder:
		return sapelliFolder;
	}

	public String getDownloadFolderPath()
	{
		return getSapelliFolder().getAbsolutePath() + File.separator + DOWNLOAD_FOLDER;
	}
	
	public String getTempFolderPath()
	{
		return getSapelliFolder().getAbsolutePath() + File.separator + TEMP_FOLDER;
	}
	
	public String getProjectFolderPath()
	{
		return getSapelliFolder().getAbsolutePath() + File.separator + PROJECT_FOLDER;
	}
	
	public String getDumpFolderPath()
	{
		return getSapelliFolder().getAbsolutePath() + File.separator + DUMP_FOLDER;
	}
	
	public String getExportFolderPath()
	{
		return getSapelliFolder().getAbsolutePath() + File.separator + EXPORT_FOLDER;
	}

	@Override
	public void onLowMemory()
	{
		super.onLowMemory();
		Debug.d("onLowMemory() called!");
	}

	@Override
	public void onTerminate()
	{
		super.onTerminate();
		// This method is for use in emulated process environments. It will never be called on
		// a production Android device, where processes are removed by simply killing them; no
		// user code (including this callback) is executed when doing so.
		Debug.d("Should never be called!");
	}

	/**
	 * Called by a StoreClient to request a ProjectStore object
	 * 
	 * @param client
	 * @return
	 * @throws Exception
	 */
	public ProjectStore getProjectStore(StoreClient client) throws Exception
	{
		if(projectStore == null)
		{
			projectStore = /*USE_PREFS_FOR_PROJECT_STORAGE ?*/ new PrefProjectStore(this); //: new DB4OProjectStore(getFilesDir(), getDemoPrefix() /*will be "" if not in demo mode*/ + DATABASE_BASENAME);
			storeClients.put(projectStore, new HashSet<StoreClient>());
		}
		storeClients.get(projectStore).add(client); //add to set of clients currently using the projectStore
		return projectStore;
	}
	
	public SapelliCollectorClient getCollectorClient(StoreClient client) throws Exception
	{
		return new SapelliCollectorClient(getProjectStore(client));
	}
	
	/**
	 * @param client
	 * @return
	 */
	public RecordStore getRecordStore(StoreClient client) throws Exception
	{
		if(recordStore == null)
		{
			//recordStore = new DB4ORecordStore(getCollectorClient(client), getFilesDir(), getDemoPrefix() /*will be "" if not in demo mode*/ + DATABASE_BASENAME);
			recordStore = new AndroidSQLiteRecordStore(getCollectorClient(client), this, getDemoPrefix() /*will be "" if not in demo mode*/ + DATABASE_BASENAME);
			storeClients.put(recordStore, new HashSet<StoreClient>());
		}
		storeClients.get(recordStore).add(client); //add to set of clients currently using the projectStore
		return recordStore;
	}
	
	/**
	 * Called by a DataAccessClient to signal it will no longer use its DataAccess object 
	 * 
	 * @param store
	 * @param client
	 */
	public void discardStoreUsage(Store store, StoreClient client)
	{
		if(store == null)
			return;
		
		// Remove client for this store:
		Set<StoreClient> clients = storeClients.get(store);
		
		if(clients != null)
			clients.remove(client);
		// Finalise if no longer used by other clients:
		if(clients == null || clients.isEmpty())
		{
			try
			{
				store.finalise();
			}
			catch(DBException ignore) { }
			storeClients.remove(store); // remove empty set

			//Slightly dirty but acceptable:
			if(store == projectStore)
				projectStore = null;
			else if(store == recordStore)
				recordStore = null;
			//else if(store == transmissionStore)
			//	transmissionStore = null;
		}
	}
	
	public void backupStores() throws Exception
	{
		File exportFolder = new File(getDumpFolderPath());
		if(!FileHelpers.createFolder(exportFolder))
			throw new Exception("Export folder (" + exportFolder.getAbsolutePath() + ") does not exist and could not be created!");
		for(Store store : new Store[] { getProjectStore(this), getRecordStore(this) })
		{
			store.backup(exportFolder);
			discardStoreUsage(store, this);
		}
	}

}
