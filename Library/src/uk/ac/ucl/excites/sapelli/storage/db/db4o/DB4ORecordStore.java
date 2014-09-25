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

package uk.ac.ucl.excites.sapelli.storage.db.db4o;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import uk.ac.ucl.excites.sapelli.shared.db.DBException;
import uk.ac.ucl.excites.sapelli.shared.db.db4o.DB4OConnector;
import uk.ac.ucl.excites.sapelli.shared.util.TimeUtils;
import uk.ac.ucl.excites.sapelli.storage.StorageClient;
import uk.ac.ucl.excites.sapelli.storage.db.RecordStore;
import uk.ac.ucl.excites.sapelli.storage.model.AutoIncrementingPrimaryKey;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.model.columns.IntegerColumn;
import uk.ac.ucl.excites.sapelli.storage.queries.RecordsQuery;
import uk.ac.ucl.excites.sapelli.storage.queries.SingleRecordQuery;
import uk.ac.ucl.excites.sapelli.storage.queries.constraints.Constraint;

import com.db4o.ObjectContainer;
import com.db4o.ObjectSet;
import com.db4o.query.Predicate;

/**
 * DB4O implementation of {@link RecordStore}.
 * 
 * DB4O storage has many known and unknown stability, performance and functionality issues.
 * We have painstakingly tried to work around the most concerning of those, but nevertheless
 * it is essential that we move away from DB4O sooner rather than later.
 * 
 * @author mstevens
 */
public class DB4ORecordStore extends RecordStore
{

	// Statics----------------------------------------------
	static public final int ACTIVATION_DEPTH = 40;
	static public final int UPDATE_DEPTH = 40;

	// Dynamics---------------------------------------------
	private ObjectContainer db4o;
	private String filename;
	
	private AutoIncrementDictionary autoIncrementDict;
	
	public DB4ORecordStore(StorageClient client, File folder, String baseFilename) throws Exception
	{
		super(client);
		this.filename = baseFilename + DATABASE_NAME_SUFFIX;
		this.db4o = DB4OConnector.open(DB4OConnector.getFile(folder, filename), Record.class, Schema.class);
		
		// Get or set the AutoIncrementDictionary:
		ObjectSet<AutoIncrementDictionary> resultSet = db4o.query(AutoIncrementDictionary.class);
		if(!resultSet.isEmpty())
			this.autoIncrementDict = resultSet.get(0);
		else
			this.autoIncrementDict = new AutoIncrementDictionary();
	}
	
	@Override
	protected void doStartTransaction()
	{
		// does nothing
	}

	@Override
	protected void doCommitTransaction() throws DBException
	{
		try
		{
			db4o.commit();
		}
		catch(Exception e)
		{
			throw new DBException("Could not commit changes to DB4O file", e);
		}
	}

	@Override
	protected void doRollbackTransaction() throws DBException
	{
		try
		{
			db4o.rollback();
		}
		catch(Exception e)
		{
			throw new DBException("Could not roll back changes to DB4O file", e);
		}
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordStore#doStore(uk.ac.ucl.excites.sapelli.storage.model.Record)
	 */
	@Override
	protected boolean doStore(Record record) throws DBException
	{
		try
		{
			boolean insert = !db4o.ext().isStored(record);
			
			// Deal with auto-incrementing primary keys:
			if(record.getSchema().getPrimaryKey() instanceof AutoIncrementingPrimaryKey)
			{
				IntegerColumn autoIncrIDColumn = ((AutoIncrementingPrimaryKey) record.getSchema().getPrimaryKey()).getColumn();
				// Set autoIncr ID:
				if(!autoIncrIDColumn.isValueSet(record)) // equivalent to if(insert)
				{
					autoIncrIDColumn.storeValue(record, autoIncrementDict.getNextID(record.getSchema()));
					// Store the the dictionary:
					db4o.store(autoIncrementDict);
				}
			}
			db4o.store(record);
			
			return insert;
		}
		catch(Exception e)
		{
			throw new DBException("DB4O exception", e, record);
		}
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordStore#retrieveAllRecords()
	 */
	@Override
	public List<Record> retrieveAllRecords()
	{
		ObjectSet<Record> resultSet = db4o.query(Record.class);
		
		// Activate result records & add to new ArrayList (list returned by DB4O doesn't allow sorting and possibly other things):
		List<Record> result = new ArrayList<Record>();
		for(Record r : resultSet)
		{
			db4o.activate(r, ACTIVATION_DEPTH);
			// Filter out records of internal schemas:
			if(!r.getSchema().isInternal())
				result.add(r);
		}
		return result;
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordStore#retrieveRecords(uk.ac.ucl.excites.sapelli.storage.queries.RecordsQuery)
	 */
	@Override
	public List<Record> retrieveRecords(final RecordsQuery query)
	{		
		// Query for records:
		ObjectSet<Record> resultSet = db4o.query(new Predicate<Record>()
		{
			private static final long serialVersionUID = 1L;

			public boolean match(Record record)
			{
				return	!record.getSchema().isInternal()													/* filter out records of internal schemas */
						&& (query.isAnySchema() || query.getSourceSchemata().contains(record.getSchema()));	/* Schema check */
			}
		});
		
		// Activate result records, filter by query constraints & add to new ArrayList (list returned by DB4O doesn't allow sorting and possibly other things):
		List<Record> result = new ArrayList<Record>();
		Constraint constraints = query.getConstraints();
		while(resultSet.hasNext())
		{
			Record r = resultSet.next();
			db4o.activate(r, ACTIVATION_DEPTH);
			if(constraints == null || constraints.isValid(r)) // Filter by constraint(s) (for some reason the DB4O query doesn't work if this happens inside the Predicate's match() method)
				result.add(r);
		}
		
		// Sort result:
		query.sort(result);
		
		// Apply limit if necessary & return result:
		int limit = query.getLimit();
		if(limit != RecordsQuery.NO_LIMIT && result.size() > limit)
			return result.subList(0, limit);
		else
			return result;
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordStore#retrieveRecord(uk.ac.ucl.excites.sapelli.storage.queries.SingleRecordQuery)
	 */
	@Override
	public Record retrieveRecord(SingleRecordQuery query)
	{
		// Run the RecordsQuery:
		List<Record> records = retrieveRecords(query.getRecordsQuery());
		
		// Run execute the SingleRecordQuery (reducing the list to 1 record), without re-running the recordsQuery, and then return the result:
		return query.execute(records, false);
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordStore#retrieveAllDeletableRecords()
	 */
	protected List<Record> retrieveAllDeletableRecords()
	{
		return db4o.query(Record.class); // also includes records of internal schemata
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.db.RecordStore#doDelete(uk.ac.ucl.excites.sapelli.storage.model.Record)
	 */
	@Override
	public void doDelete(Record record) throws DBException
	{
		try
		{
			db4o.delete(record);
		}
		catch(Exception e)
		{
			throw new DBException("Error upon deleting record", e, record);
		}
	}
	
	@Override
	public void finalise() throws DBException
	{
		commitTransaction(); // because DB4O does not have explicit opening of transactions (it is always using one) we should always commit before closing.
		super.finalise();
	}

	@Override
	protected void close() throws DBException
	{
		try
		{
			db4o.close();
		}
		catch(Exception e)
		{
			throw new DBException("Exception upon closing DB4O file", e);
		}
	}

	@Override
	protected void doBackup(File destinationFolder) throws DBException
	{
		try
		{
			db4o.ext().backup(DB4OConnector.getFile(destinationFolder, filename + BACKUP_SUFFIX + "_" + TimeUtils.getTimestampForFileName()).getAbsolutePath());
		}
		catch(Exception e)
		{
			throw new DBException("Exception upon backing-up the DB4O file", e);
		}
	}
	
	/**
	 * Helper class which does the book keeping for auto-incrementing primary keys
	 * 
	 * @author mstevens 
	 */
	private class AutoIncrementDictionary extends HashMap<Schema, Long>
	{
		
		private static final long serialVersionUID = 2L;

		public Long getNextID(Schema schema)
		{
			// Check for auto incrementing key:
			if(!(schema.getPrimaryKey() instanceof AutoIncrementingPrimaryKey))
				throw new IllegalArgumentException("Schema must have an auto-incrementing primary key");
			// Next id:
			long next = (containsKey(schema) ? get(schema) : -1l) + 1;
			// TODO deal with max reached!!!
			// Store it:
			put(schema, next); // hash map always keeps the last used id
			// Return it:
			return next;
		}
		
	}

}
