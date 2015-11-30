/**'
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

package uk.ac.ucl.excites.sapelli.transmission;

import java.util.List;
import java.util.Set;

import uk.ac.ucl.excites.sapelli.shared.db.StoreHandle;
import uk.ac.ucl.excites.sapelli.shared.db.StoreHandle.StoreCreator;
import uk.ac.ucl.excites.sapelli.shared.db.StoreHandle.StoreSetter;
import uk.ac.ucl.excites.sapelli.shared.db.StoreHandle.StoreUser;
import uk.ac.ucl.excites.sapelli.shared.db.exceptions.DBException;
import uk.ac.ucl.excites.sapelli.storage.StorageClient;
import uk.ac.ucl.excites.sapelli.storage.StorageObserver;
import uk.ac.ucl.excites.sapelli.storage.model.Column;
import uk.ac.ucl.excites.sapelli.storage.model.RecordReference;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.transmission.control.TransmissionController;
import uk.ac.ucl.excites.sapelli.transmission.db.TransmissionStore;
import uk.ac.ucl.excites.sapelli.transmission.model.Correspondent;
import uk.ac.ucl.excites.sapelli.transmission.model.Payload;
import uk.ac.ucl.excites.sapelli.transmission.model.Transmission;

/**
 * @author mstevens
 *
 */
public abstract class TransmissionClient extends StorageClient
{

	// STATICS-------------------------------------------------------
	/**
	 * Flag indicating that a Schema has been defined at the Transmission layer of the Sapelli Library
	 */
	static private final int SCHEMA_FLAG_TRANSMISSION_LAYER =	1 << 6;
	
	/**
	 * Schema flag indicating that records of the Schema can be transmitted using the Transmission/Payload classes
	 */
	static public final int SCHEMA_FLAG_TRANSMITTABLE = 		1 << 7;
	
	// Note: flag bits 8 & 9 are reserved for future Transmission layer usage
	
	/**
	 * Flags used on "internal" Transmission layer Schemata
	 */
	static public final int SCHEMA_FLAGS_TRANSMISSION_INTERNAL = SCHEMA_FLAG_TRANSMISSION_LAYER;
	
	/**
	 * ID for the reserved Transmission Management Model ({@link TransmissionStore#TRANSMISSION_MANAGEMENT_MODEL})
	 */
	static public final long TRANSMISSION_MANAGEMENT_MODEL_ID = 0;
	
	// Add tableName prefix & reserved model (in that order!):
	static
	{
		AddTableNamePrefix(SCHEMA_FLAG_TRANSMISSION_LAYER, "Transmission_");
		AddReservedModel(TransmissionStore.TRANSMISSION_MANAGEMENT_MODEL);
	}
	
	// DYNAMICS------------------------------------------------------
	public final StoreHandle<TransmissionStore> transmissionStoreHandle = new StoreHandle<TransmissionStore>(new StoreCreator<TransmissionStore>()
	{
		@Override
		public void createAndSetStore(StoreSetter<TransmissionStore> setter) throws DBException
		{
			setter.setAndInitialise(new TransmissionStore(TransmissionClient.this));
		}
	});
	
	public TransmissionClient()
	{
		new TransmissionStorageObserver(); // no need to hold a reference to it, the object will register itself as a StorageObserver
	}
	
	/**
	 * TODO
	 * 
	 * @param nonBuiltinType
	 * @return
	 */
	public abstract Payload createCustomPayload(int nonBuiltinType);
	
	/**
	 * Override to add support for receiving custom payload or the change handling of built-in payload types
	 * 
	 * @return
	 */
	public TransmissionController.PayloadReceiver getCustomPayloadReceiver()
	{
		return null;
	}
	
	/**
	 * @param transmissionType
	 * @param address
	 * @return
	 */
	public Correspondent createCustomCorrespondent(Transmission.Type transmissionType, String address)
	{
		return null;
	}
	
	/**
	 * Returns a list of Correspondent interested in (updates to) records of the given Schema.
	 * 
	 * @param schema
	 * @return
	 */
	public abstract List<Correspondent> getReceiversFor(Schema schema);
	
	/**
	 * Returns columns from the given schema that should not be transmitted.
	 * It is assumed these are optional columns, or (TODO once this is supported) non-optional columns with a default value.
	 * 
	 * @param schema
	 * @return
	 */
	public abstract Set<Column<?>> getNonTransmittableColumns(Schema schema);
	
	/**
	 * Helper class to receive store event updates and let TransmissionStore update its "TransmitableRecords" table accordingly. 
	 * 
	 * @author mstevens
	 */
	private final class TransmissionStorageObserver implements StorageObserver, StoreUser
	{
		
		private TransmissionStore tStore;
		
		public TransmissionStorageObserver()
		{
			/* Note:
			 * Do *not* initialise tStore here as it causes a call to CollectorApp#getFileStorageProvider()
			 * before CollectorApp#initialiseFileStorage() has been called. */
			
			// Register ourself as an observer to receive updates about storage events:
			addObserver(this);
		}
		
		private boolean init()
		{
			if(tStore == null)
			{
				try
				{
					tStore = transmissionStoreHandle.getStore(this);
				}
				catch(DBException e)
				{
					e.printStackTrace(System.err); // TODO propagate upwards (to android) error logging
					return false;
				}
			}
			return true;
		}
		
		@Override
		public void storageEvent(RecordOperation operation, RecordReference recordRef)
		{
			if(init() /*make sure we have tStore*/ && recordRef.getReferencedSchema().hasFlags(SCHEMA_FLAG_TRANSMITTABLE))
				receiverLoop : for(Correspondent receiver : getReceiversFor(recordRef.getReferencedSchema()))
				{
					switch(operation)
					{
						case Inserted :
						case Updated :
							tStore.storeTransmittableRecord(receiver, recordRef, null); //TODO will this wipe tosend rec's for same rec that already had a transmission?
							break;
						case Deleted :
							tStore.deleteTransmittableRecord(recordRef); // record will be forgotten about for each receiver ...
							break receiverLoop; // ... so we are done here
						default :
							throw new IllegalArgumentException("Unknown " + RecordOperation.class.getSimpleName());
					}
				}
		}
		
		@Override
		public void finalize()
		{
			transmissionStoreHandle.doneUsing(this);
		}
		
	}

}
