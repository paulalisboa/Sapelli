/**
 * Sapelli data collection platform: http://sapelli.org
 * 
 * Copyright 2012-2016 University College London - ExCiteS group
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

package uk.ac.ucl.excites.sapelli.storage.eximport;

import java.util.List;

import org.joda.time.format.DateTimeFormatter;

import uk.ac.ucl.excites.sapelli.shared.util.TimeUtils;
import uk.ac.ucl.excites.sapelli.storage.model.Record;

public interface Exporter
{

	static public final String ATTRIBUTE_EXPORTED_AT = "exportedAt";
	
	static public final DateTimeFormatter ExportedAtFormatter = TimeUtils.ISOWithMSFormatter;
	
	static public enum Format
	{
		XML,
		CSV
	}
	
	/**
	 * @param records
	 * @param description - may be null or empty
	 * @return
	 */
	public ExportResult export(List<Record> records, String description);
	
}
