/**
 * 
 */
package uk.ac.ucl.excites.sapelli.shared.util;

/**
 * Abstract superclass for key-value mapping classes where both key and value are Strings.
 * Provides helpful parsing methods for various types.
 * Subclassed by XMLAttributes & FieldParameters.
 * 
 * @author mstevens
 *
 */
public abstract class Parameters
{
	
	// Static
	public static final String ENABLED = "enabled";
	public static final String DISABLED = "disabled";
	
	public abstract String getValue(String param);
	
	public abstract boolean contains(String param);
	
	/**
	 * Read parameter
	 * 
	 * @param param
	 * @param defaultValue
	 * @return
	 */
	public String getValue(String param, String defaultValue)
	{	
		if(contains(param))
			return getValue(param);
		else
			return defaultValue;
	}
	
	/**
	 * Read a required String attribute with name {@code attributeName} in a tag with {@code qName}, using the passed {@code attributes} collection.
	 * 
	 * @param qName
	 * @param attributeName
	 * @param trim
	 * @param allowEmpty
	 * @return
	 * @throws Exception	when no matching attribute is found
	 */
	public String getRequiredString(String qName, String attributeName, boolean trim, boolean allowEmpty) throws Exception
	{
		return getRequiredString(qName, attributeName, null, trim, allowEmpty);
	}

	/**
	 * Read a required String attribute with a name from {@code attributeNames} (tried in order, first existing attribute wins) in a tag with {@code qName}, using the passed {@code attributes} collection.
	 * 
	 * @param qName
	 * @param trim
	 * @param allowEmpty
	 * @param attributeNames	alternative attribute names ("synonyms")
	 * @return
	 * @throws Exception	when no matching attribute is found
	 */
	public String getRequiredString(String qName, boolean trim, boolean allowEmpty, String... attributeNames) throws Exception
	{
		return getRequiredString(qName, null, trim, allowEmpty, attributeNames);
	}
	
	/**
	 * Read a required String attribute with name {@code attributeName} in a tag with {@code qName}, using the passed {@code attributes} collection.
	 * The {@code reason} explains why the attribute is required.
	 * 
	 * @param qName
	 * @param attributeName
	 * @param reason
	 * @param trim
	 * @param allowEmpty
	 * @return
	 * @throws Exception	when no matching attribute is found
	 */
	public String getRequiredString(String qName, String attributeName, String reason, boolean trim, boolean allowEmpty) throws Exception
	{
		return getRequiredString(qName, reason, trim, allowEmpty, attributeName);
	}
	
	/**
	 * Read a required String attribute with a name from {@code attributeNames} (tried in order, first existing attribute wins) in a tag with {@code qName}, using the passed {@code attributes} collection.
	 * The {@code reason} explains why the attribute is required.
	 * 
	 * @param qName
	 * @param reason
	 * @param trim
	 * @param allowEmpty
	 * @param attributeNames	alternative attribute names ("synonyms")
	 * @return
	 * @throws Exception	when no matching attribute is found
	 */
	public String getRequiredString(String qName, String reason, boolean trim, boolean allowEmpty, String... attributeNames) throws Exception
	{
		for(String attributeName : attributeNames)
		{
			String value = getValue(attributeName);
			if(value != null)
			{
				if(allowEmpty || !"".equals(trim ? value.trim() : value))
					return trim ? value.trim() : value;
				else
					throw new Exception("Required attribute " + attributeName + " on tag <" + qName + "> is present but has an empty value."); // don't try next alternative because attribute was present
			}
			//else :  there is no attribute with the attributeName, try next alternative
		}
		throw new Exception("There is no attribute with name " + StringUtils.join(attributeNames, " or ") + ", this is required for tag <" + qName + ">" + (reason != null ? " (" + reason + ")" : "") + ".");
	}
	
	/**
	 * Read an optional String attribute with name {@code attributeName}, using the passed {@code attributes} collection.
	 * When no such attribute exists the {@code defaultValue} is returned.
	 * 
	 * @param attributeName
	 * @param defaultValue
	 * @param trim
	 * @param allowEmpty
	 * @return
	 */
	public String getString(String attributeName, String defaultValue, boolean trim, boolean allowEmpty)
	{
		return getString(defaultValue, trim, allowEmpty, attributeName);
	}
	
	/**
	 * Read an optional String attribute with name from {@code attributeNames} (tried in order, first existing attribute wins), using the passed {@code attributes} collection.
	 * When no such attribute exists the {@code defaultValue} is returned.
	 * 
	 * @param defaultValue
	 * @param trim
	 * @param allowEmpty
	 * @param attributeNames	alternative attribute names ("synonyms")
	 * @return
	 */
	public String getString(String defaultValue, boolean trim, boolean allowEmpty, String... attributeNames)
	{
		for(String attributeName : attributeNames)
		{
			String value = getValue(attributeName);
			if(value != null)
			{
				if(allowEmpty || !"".equals(trim ? value.trim() : value))
					return trim ? value.trim() : value;
				else
					return defaultValue; // attribute is present but empty -> don't try next alternative and return defaultValue
			}
			//else :  there is no attribute with the attributeName, try next alternative
		}
		return defaultValue;
	}

	/**
	 * Read an optional boolean attribute with name {@code attributeName}, using the passed {@code attributes} collection.
	 * When no such attribute exists the {@code defaultValue} is returned.
	 * 
	 * @param attributeName
	 * @param defaultValue
	 * @return
	 */
	public boolean getBoolean(String attributeName, boolean defaultValue)
	{
		return getBoolean(defaultValue, attributeName);
	}
	
	/**
	 * Read an optional boolean attribute with a name from {@code attributeNames} (tried in order, first existing attribute wins), using the passed {@code attributes} collection.
	 * When no such attribute exists the {@code defaultValue} is returned.
	 * 
	 * @param defaultValue
	 * @param attributeNames	alternative attribute names ("synonyms")
	 * @return
	 */
	public boolean getBoolean(boolean defaultValue, String... attributeNames)
	{
		for(String attributeName : attributeNames)
		{
			String strVal = getValue(attributeName);
			if(strVal == null)
				continue; // there is no attribute with the attributeName, try next alternative
			else
			{
				strVal = strVal.trim();
				if(strVal.isEmpty())
					return defaultValue;
				else if(strVal.equalsIgnoreCase(Boolean.TRUE.toString()))
					return Boolean.TRUE;
				else if(strVal.equalsIgnoreCase(Boolean.FALSE.toString()))
					return Boolean.FALSE;
				else if(strVal.equalsIgnoreCase(ENABLED))
					return Boolean.TRUE;
				else if(strVal.equalsIgnoreCase(DISABLED))
					return Boolean.FALSE;
				else
					return defaultValue;				
			}
		}
		return defaultValue;
	}

	/**
	 * Read a required integer attribute with name {@code attributeName} in a tag with {@code qName}, using the passed {@code attributes} collection.
	 * 
	 * @param qName
	 * @param attributeName
	 * @return
	 * @throws Exception	when no matching attribute is found
	 * @throws NumberFormatException when the attribute value string does not hold a valid integer (e.g. because it is empty)
	 */
	public int getRequiredInteger(String qName, String attributeName) throws Exception, NumberFormatException
	{
		return getRequiredInteger(qName, attributeName, (String) null);
	}
	
	/**
	 * Read a required integer attribute with a name from {@code attributeNames} (tried in order, first existing attribute wins) in a tag with {@code qName}, using the passed {@code attributes} collection.
	 * 
	 * @param qName
	 * @param attributeNames	alternative attribute names ("synonyms")
	 * @return
	 * @throws Exception	when no matching attribute is found
	 * @throws NumberFormatException when the attribute value string does not hold a valid integer (e.g. because it is empty)
	 */
	public int getRequiredInteger(String qName, String... attributeNames) throws Exception, NumberFormatException
	{
		return getRequiredInteger(qName, null, attributeNames);
	}
	
	/**
	 * Read a required integer attribute with name {@code attributeName} in a tag with {@code qName}, using the passed {@code attributes} collection.
	 * The {@code reason} explains why the attribute is required.
	 * 
	 * @param qName
	 * @param attributeName
	 * @param reason
	 * @return
	 * @throws Exception	when no matching attribute is found
	 * @throws NumberFormatException when the attribute value string does not hold a valid integer (e.g. because it is empty)
	 */
	public int getRequiredInteger(String qName, String attributeName, String reason) throws Exception, NumberFormatException
	{
		return getRequiredInteger(qName, reason, new String[] { attributeName });
	}
	
	/**
	 * Read a required integer attribute with a name from {@code attributeNames} (tried in order, first existing attribute wins) in a tag with {@code qName}, using the passed {@code attributes} collection.
	 * The {@code reason} explains why the attribute is required.
	 * 
	 * @param qName
	 * @param reason
	 * @param attributeNames	alternative attribute names ("synonyms")
	 * @return
	 * @throws Exception	when no matching attribute is found
	 * @throws NumberFormatException when the attribute value string does not hold a valid integer (e.g. because it is empty)
	 */
	public int getRequiredInteger(String qName, String reason, String... attributeNames) throws Exception, NumberFormatException
	{
		for(String attributeName : attributeNames)
		{
			String strVal = getValue(attributeName);
			if(strVal != null)
				return Integer.parseInt(strVal.trim()); // throws NumberFormatException
			//else :  there is no attribute with the attributeName, try next alternative
		}
		throw new Exception("There is no attribute with name " + StringUtils.join(attributeNames, " or ") + ", this is required for tag " + qName + (reason != null ? " (" + reason + ")" : "") + ".");
	}
	
	/**
	 * Read an optional integer attribute with name {@code attributeName}, using the passed {@code attributes} collection.
	 * When no such attribute exists the {@code defaultValue} is returned.
	 * 
	 * @param attributeName
	 * @param defaultValue
	 * @throws NumberFormatException when the attribute value string does not hold a valid integer (e.g. because it is empty)
	 * @return
	 */
	public int getInteger(String attributeName, int defaultValue) throws NumberFormatException
	{
		return getInteger(defaultValue, attributeName);
	}

	/**
	 * Read an optional integer attribute with a name from {@code attributeNames} (tried in order, first existing attribute wins), using the passed {@code attributes} collection.
	 * When no such attribute exists the {@code defaultValue} is returned.
	 * 
	 * @param defaultValue
	 * @param attributeNames	alternative attribute names ("synonyms")
	 * @throws NumberFormatException when the attribute value string does not hold a valid integer (e.g. because it is empty)
	 * @return
	 */
	public int getInteger(int defaultValue, String... attributeNames) throws NumberFormatException
	{
		for(String attributeName : attributeNames)
		{
			String strVal = getValue(attributeName);
			if(strVal == null)
				continue; // there is no attribute with the attributeName, try next alternative
			else
			{
				if(strVal.trim().isEmpty())
					return defaultValue;
				else
					return Integer.parseInt(strVal.trim()); // throws NumberFormatException
			}
		}
		return defaultValue;
	}
	
	/**
	 * Read a required long attribute with name {@code attributeName} in a tag with {@code qName}, using the passed {@code attributes} collection.
	 * 
	 * @param qName
	 * @param attributeName
	 * @return
	 * @throws Exception	when no matching attribute is found
	 * @throws NumberFormatException when the attribute value string does not hold a valid integer (e.g. because it is empty)
	 */
	public long getRequiredLong(String qName, String attributeName) throws Exception, NumberFormatException
	{
		return getRequiredLong(qName, attributeName, (String) null);
	}
	
	/**
	 * Read a required long attribute with a name from {@code attributeNames} (tried in order, first existing attribute wins) in a tag with {@code qName}, using the passed {@code attributes} collection.
	 * 
	 * @param qName
	 * @param attributeNames	alternative attribute names ("synonyms")
	 * @return
	 * @throws Exception	when no matching attribute is found
	 * @throws NumberFormatException when the attribute value string does not hold a valid integer (e.g. because it is empty)
	 */
	public long getRequiredLong(String qName, String... attributeNames) throws Exception, NumberFormatException
	{
		return getRequiredLong(qName, null, attributeNames);
	}
	
	/**
	 * Read a required long attribute with name {@code attributeName} in a tag with {@code qName}, using the passed {@code attributes} collection.
	 * The {@code reason} explains why the attribute is required.
	 * 
	 * @param qName
	 * @param attributeName
	 * @param reason
	 * @return
	 * @throws Exception	when no matching attribute is found
	 * @throws NumberFormatException when the attribute value string does not hold a valid integer (e.g. because it is empty)
	 */
	public long getRequiredLong(String qName, String attributeName, String reason) throws Exception, NumberFormatException
	{
		return getRequiredLong(qName, reason, new String[] { attributeName });
	}
	
	/**
	 * Read a required long attribute with a name from {@code attributeNames} (tried in order, first existing attribute wins) in a tag with {@code qName}, using the passed {@code attributes} collection.
	 * The {@code reason} explains why the attribute is required.
	 * 
	 * @param qName
	 * @param reason
	 * @param attributeNames	alternative attribute names ("synonyms")
	 * @return
	 * @throws Exception	when no matching attribute is found
	 * @throws NumberFormatException when the attribute value string does not hold a valid integer (e.g. because it is empty)
	 */
	public long getRequiredLong(String qName, String reason, String... attributeNames) throws Exception, NumberFormatException
	{
		for(String attributeName : attributeNames)
		{
			String strVal = getValue(attributeName);
			if(strVal != null)
				return Long.parseLong(strVal.trim()); // throws NumberFormatException
			//else :  there is no attribute with the attributeName, try next alternative
		}
		throw new Exception("There is no attribute with name " + StringUtils.join(attributeNames, " or ") + ", this is required for tag " + qName + (reason != null ? " (" + reason + ")" : "") + ".");
	}
	
	/**
	 * Read an optional long attribute with name {@code attributeName}, using the passed {@code attributes} collection.
	 * When no such attribute exists the {@code defaultValue} is returned.
	 * 
	 * @param attributeName
	 * @param defaultValue
	 * @throws NumberFormatException when the attribute value string does not hold a valid integer (e.g. because it is empty)
	 * @return
	 */
	public long getLong(String attributeName, long defaultValue) throws NumberFormatException
	{
		return getLong(defaultValue, attributeName);
	}

	/**
	 * Read an optional long attribute with a name from {@code attributeNames} (tried in order, first existing attribute wins), using the passed {@code attributes} collection.
	 * When no such attribute exists the {@code defaultValue} is returned.
	 * 
	 * @param defaultValue
	 * @param attributeNames	alternative attribute names ("synonyms")
	 * @throws NumberFormatException when the attribute value string does not hold a valid integer (e.g. because it is empty)
	 * @return
	 */
	public long getLong(long defaultValue, String... attributeNames) throws NumberFormatException
	{
		for(String attributeName : attributeNames)
		{
			String strVal = getValue(attributeName);
			if(strVal == null)
				continue; // there is no attribute with the attributeName, try next alternative
			else
			{
				if(strVal.trim().isEmpty())
					return defaultValue;
				else
					return Long.parseLong(strVal.trim()); // throws NumberFormatException
			}
		}
		return defaultValue;
	}

	/**
	 * Read an optional float attribute with name {@code attributeName}, using the passed {@code attributes} collection.
	 * When no such attribute exists the {@code defaultValue} is returned.
	 * 
	 * @param attributeName
	 * @param defaultValue
	 * @throws NumberFormatException when the attribute value string does not hold a valid float (e.g. because it is empty)
	 * @return
	 */
	public float getFloat(String attributeName, float defaultValue) throws NumberFormatException
	{
		return getFloat(defaultValue, attributeName);
	}
	
	/**
	 * Read an optional float attribute with a name from {@code attributeNames} (tried in order, first existing attribute wins), using the passed {@code attributes} collection.
	 * When no such attribute exists the {@code defaultValue} is returned.
	 * 
	 * @param defaultValue
	 * @param attributeNames	alternative attribute names ("synonyms")
	 * @throws NumberFormatException when the attribute value string does not hold a valid float (e.g. because it is empty)
	 * @return
	 */
	public float getFloat(float defaultValue, String... attributeNames) throws NumberFormatException
	{
		for(String attributeName : attributeNames)
		{
			String strVal = getValue(attributeName);
			if(strVal == null)
				continue; // there is no attribute with the attributeName, try next alternative
			else
			{
				if(strVal.trim().isEmpty())
					return defaultValue;
				else
					return Float.parseFloat(strVal.trim()); // throws NumberFormatException
			}
		}
		return defaultValue;
	}

}