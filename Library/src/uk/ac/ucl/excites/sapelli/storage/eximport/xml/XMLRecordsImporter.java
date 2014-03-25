/**
 * 
 */
package uk.ac.ucl.excites.sapelli.storage.eximport.xml;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import uk.ac.ucl.excites.sapelli.shared.util.StringUtils;
import uk.ac.ucl.excites.sapelli.shared.util.xml.DocumentParser;
import uk.ac.ucl.excites.sapelli.storage.StorageClient;
import uk.ac.ucl.excites.sapelli.storage.eximport.xml.XMLRecordsExporter.CompositeMode;
import uk.ac.ucl.excites.sapelli.storage.model.Column;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import uk.ac.ucl.excites.sapelli.storage.model.RecordColumn;
import uk.ac.ucl.excites.sapelli.storage.model.Schema;
import uk.ac.ucl.excites.sapelli.storage.model.columns.LocationColumn;
import uk.ac.ucl.excites.sapelli.storage.types.Location;

/**
 * XML {@link DocumentParser} that imports {@link Record}s from XML files generated by the current and previous versions of {@link XMLRecordsExporter}.
 * 
 * <p>Supported formats:<br/>
 * 	- v1.x exports, with both v1.x versions of the {@link Location} serialisation format (see {@link Location#parseV1X(String)}).<br/>
 *  - All 3 {@link CompositeMode}s supported by {@link XMLRecordsExporter}: {@link CompositeMode#As_String}, {@link CompositeMode#As_flat_tags} & {@link CompositeMode#As_nested_tags} 
 * 
 * @author mstevens
 */
public class XMLRecordsImporter extends DocumentParser
{

	protected StorageClient client;
	protected Record currentRecord;
	protected boolean v1xRecord;
	protected Stack<Column<?>> columnStack;
	protected List<Record> records;

	public XMLRecordsImporter(StorageClient client)
	{
		super();
		this.client = client;
		columnStack = new Stack<Column<?>>();
	}

	public List<Record> importFrom(File xmlFile) throws Exception
	{
		records = new ArrayList<Record>();
		columnStack.clear();
		parse(open(xmlFile));
		return records;
	}

	@Override
	public void startDocument() throws SAXException
	{
		// does nothing (for now)
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
	{
		// <RecordsExport>
		if(qName.equals(XMLRecordsExporter.TAG_RECORDS_EXPORT))
		{
			// do nothing
		}
		// <Record>
		else if(qName.equals(Record.TAG_RECORD))
		{
			if(currentRecord != null)
				throw new SAXException("Records cannot be nested!");
			
			Schema schema = null;
			String schemaDescr = null;
			if(attributes.getIndex(Schema.V1X_ATTRIBUTE_SCHEMA_ID) != -1)
			{	//This file contains records exported by Sapelli v1.x
				int schemaID = readRequiredIntegerAttribute(Record.TAG_RECORD, Schema.V1X_ATTRIBUTE_SCHEMA_ID, "because this is a v1.x record", attributes);
				int schemaVersion = readIntegerAttribute(Schema.V1X_ATTRIBUTE_SCHEMA_VERSION, Schema.V1X_DEFAULT_SCHEMA_VERSION, attributes);
				schema = client.getSchemaV1(schemaID, schemaVersion);
				schemaDescr = "v1.x schema with ID " + schemaID + " and version " + schemaVersion;
				v1xRecord = true;
			}
			else
			{
				long schemaID = readRequiredLongAttribute(Record.TAG_RECORD, Schema.ATTRIBUTE_SCHEMA_ID, attributes);
				schema = client.getSchema(schemaID);
				schemaDescr = "schema with ID " + schemaID;
				v1xRecord = false;
			}
			if(schema == null)
				addWarning("Record skipped because " + schemaDescr + " is unknown, please load the appropriate project.");
			else
				currentRecord = client.getNewRecord(schema);
			// TODO transmission? sent/received
		}
		// Record columns:
		else if(currentRecord != null)
		{
			Record record = currentRecord;
			for(String colName : qName.split("\\" + RecordColumn.QUALIFIED_NAME_SEPARATOR))
			{
				// Deal with previous (record)column:
				if(!columnStack.isEmpty())
				{
					RecordColumn<?> recCol = ((RecordColumn<?>) columnStack.peek());
					// Create subrecord instance:
					if(!record.isValueSet(recCol))
						recCol.storeObject(record, recCol.getNewRecord());
					// Set subrecord as record:
					record = recCol.retrieveValue(record);
				}
				// Deal with current column:
				Column<?> col = record.getSchema().getColumn(colName);
				columnStack.push(col); // even when null! (to deal with unrecognised columns)
				if(col == null)
				{
					addWarning("Column " + colName + " does not exist in " + record.getSchema().toString());
					break;
				}
			}
		}
		// <?>
		else
			addWarning("Ignored unrecognised or invalidly placed element \"" + qName + "\".");
	}

	@Override
	public void characters(char ch[], int start, int length) throws SAXException
	{
		// Reached leaf value string...
		if(currentRecord != null && !columnStack.isEmpty() && columnStack.peek() != null)
		{
			// Get the column at the top of the columnStack:
			Column<?> column = columnStack.peek();
			
			// Get the (sub)record corresponding to the column:
			Record record = currentRecord;
			for(Column<?> col : columnStack)
				if(col instanceof RecordColumn && col != column)
					record = ((RecordColumn<?>) col).retrieveValue(record);
			
			// Get string representation of column value:
			String valueString = new String(ch, start, length).trim();
			if(valueString.isEmpty())
				return; // empty String are treated as null so there is no value to set. We return here to avoid errors when setting null values on non-optional columns.
			
			// Parse & store value:
			try
			{
				if(v1xRecord && column instanceof LocationColumn)
					// Backwards compatibility with old location formats:
					column.storeObject(record, Location.parseV1X(valueString));
				else
					column.parseAndStoreValue(record, valueString);
			}
			catch(Exception e)
			{
				addWarning(e.getClass().getName() + " upon parsing value (" + valueString + ") for " + column.toString() + " \"" + column.getName() + "\"" + (e.getMessage() != null ? ", cause: " + e.getMessage() : "."));
			}
		}
	}
	
	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException
	{
		// </Record>
		if(qName.equals(Record.TAG_RECORD))
		{
			records.add(currentRecord);
			currentRecord = null;
		}
		// Record columns:
		else if(currentRecord != null && !columnStack.isEmpty())
		{
			for(int c = 0; c <= StringUtils.countOccurances(qName, RecordColumn.QUALIFIED_NAME_SEPARATOR); c++)
				columnStack.pop();
		}
	}

	@Override
	public void endDocument() throws SAXException
	{
		// does nothing for now
	}

}
