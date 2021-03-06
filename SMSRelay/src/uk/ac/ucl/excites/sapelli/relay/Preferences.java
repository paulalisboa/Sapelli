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

package uk.ac.ucl.excites.sapelli.relay;

import java.util.regex.Pattern;

import uk.ac.ucl.excites.sapelli.relay.R;
import uk.ac.ucl.excites.sapelli.relay.util.Debug;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Patterns;

/**
 * This class contains the preferences
 * 
 * @author Michalis Vitos
 * 
 */
public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener
{

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.background_preferences);
		PreferenceManager.setDefaultValues(Preferences.this, R.xml.background_preferences, false);

		// Register a listener to check the validity of the URL
		SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		mSharedPreferences.registerOnSharedPreferenceChangeListener(this);
	}

	/**
	 * Get the server address where the application is sending (POST) the incoming messages
	 * 
	 * @param mContext
	 * @return
	 */
	public static String getServerAddress(Context mContext)
	{
		SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
		return mSharedPreferences.getString("serverAddress", "").trim();
	}

	/**
	 * Get the number of minutes that the service is sending messages
	 * 
	 * @param mContext
	 * @return
	 */
	public static int getTimeSchedule(Context mContext)
	{
		SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
		return Integer.parseInt(mSharedPreferences.getString("timeSchedule", "10"));
	}

	public static SharedPreferences getSharedPreferences(Context context)
	{
		SharedPreferences mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
		return mSharedPreferences;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		// Check for a valid URL pattern
		if(key.equals("serverAddress"))
		{
			String value = sharedPreferences.getString("serverAddress", "");

			if(!Pattern.matches(Patterns.WEB_URL.toString(), value))
			{
				// Show a message
				final AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("Invalid Input");
				builder.setMessage("Please insert a valid URL address");
				builder.setPositiveButton(android.R.string.ok, null);
				builder.show();
			}
			else
			{
				// Call the Service
				Intent mIntent = new Intent(this, BackgroundService.class);
				startService(mIntent);
			}
		}
		else if(key.equals("timeSchedule"))
		{
			// Call the Service
			Intent mIntent = new Intent(this, BackgroundService.class);
			startService(mIntent);
		}
	}

	public static void printPreferences(Context context)
	{
		Debug.d("------------ Preferences: -------------");
		Debug.d("ServerAddress: " + getServerAddress(context));
		Debug.d("TimeSchedule: " + getTimeSchedule(context));
	}
}