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

package uk.ac.ucl.excites.sapelli.storage.model.columns;

import java.io.IOException;

import uk.ac.ucl.excites.sapelli.shared.io.BitInputStream;
import uk.ac.ucl.excites.sapelli.shared.io.BitOutputStream;
import uk.ac.ucl.excites.sapelli.storage.model.Column;
import uk.ac.ucl.excites.sapelli.storage.model.ColumnSet;
import uk.ac.ucl.excites.sapelli.storage.model.ValueSet;
import uk.ac.ucl.excites.sapelli.storage.util.InvalidColumnException;
import uk.ac.ucl.excites.sapelli.storage.util.InvalidValueException;
import uk.ac.ucl.excites.sapelli.storage.visitors.ColumnVisitor;

/**
 * A column for 32 bit (float) or 64 bit (double) floating point numbers
 * 
 * @author mstevens
 */
public class FloatColumn extends NumberColumn<Double>
{	
	
	static private final long serialVersionUID = 2L;
	
	static public final boolean DEFAULT_DOUBLE_PRECISION = false; // 32 bit (float) by default
	static public final boolean DEFAULT_SIGNEDNESS = true; // allow signed values by default
	
	private final boolean doublePrecision;
	private final boolean signed;
	
	public FloatColumn(String name, boolean optional)
	{
		this(name, optional, DEFAULT_SIGNEDNESS, DEFAULT_DOUBLE_PRECISION);
	}
	
	/**
	 * @param name
	 * @param optional
	 * @param signed
	 * @param doublePrecision
	 */
	public FloatColumn(String name, boolean optional, boolean signed, boolean doublePrecision)
	{
		this(name, optional, signed, doublePrecision, null);
	}
	
	/**
	 * @param name
	 * @param optional
	 * @param signed
	 * @param doublePrecision
	 * @param defaultValue
	 */
	public FloatColumn(String name, boolean optional, boolean signed, boolean doublePrecision, Double defaultValue)
	{
		super(name, optional, defaultValue);
		this.doublePrecision = doublePrecision;
		this.signed = signed;
	}

	@Override
	protected FloatColumn createCopy()
	{
		return new FloatColumn(name, optional, signed, doublePrecision, defaultValue);
	}
	
	/**
	 * Float version of {@link FloatColumn#storeValue(ValueSet, Double)}
	 * 
	 * @param valueSet
	 * @param value
	 * @throws InvalidColumnException when this column is not part of the valueSet's {@link ColumnSet}, nor compatible with a column by the same name that is
	 * @throws InvalidValueException when the given value is invalid
	 * @throws NullPointerException if value is {@code null} on an non-optional column, or if the valueSet is {@code null}
	 */
	public void storeValue(ValueSet<?> valueSet, Float value) throws InvalidColumnException, InvalidValueException, NullPointerException
	{
		Double doubleValue = (value != null ? Double.valueOf(value.floatValue()) : null);
		storeValue(valueSet, doubleValue);
	}
	
	/**
	 * Converts Numbers (Floats, Integers, Shorts, etc.) to Doubles
	 * 
	 * @param value possibly null
	 * @return
	 * @throws ClassCastException when the value is not a {@link Number}
	 * @see uk.ac.ucl.excites.sapelli.storage.model.Column#convert(java.lang.Object)
	 */
	@Override
	public Double convert(Object value)
	{
		return (Double) (value == null ? null : (value instanceof Double ? value : Double.valueOf(((Number) value).doubleValue())));
	}
	
	/**
	 * @param valueSet
	 * @param nullReplacement
	 * @return
	 * @throws NullPointerException if the given {@link ValueSet} is {@code null}
	 * @throws InvalidColumnException when this column is not part of the valueSet's {@link ColumnSet}, nor compatible with a column by the same name that is
	 */
	public double getPrimitiveDouble(ValueSet<?> valueSet, double nullReplacement) throws NullPointerException, InvalidColumnException
	{
		Double doubleValue = retrieveValue(valueSet);
		if(doubleValue == null)
			return nullReplacement;
		return doubleValue.doubleValue();
	}
	
	/**
	 * @param valueSet
	 * @param nullReplacement
	 * @return
	 * @throws NullPointerException if the given {@link ValueSet} is {@code null}
	 * @throws InvalidColumnException when this column is not part of the valueSet's {@link ColumnSet}, nor compatible with a column by the same name that is
	 */
	public float getPrimitiveFloat(ValueSet<?> valueSet, float nullReplacement) throws NullPointerException, InvalidColumnException
	{
		Double doubleValue = retrieveValue(valueSet);
		if(doubleValue == null)
			return nullReplacement;
		return doubleValue.floatValue();
	}

	/**
	 * @param value the String to parse (can be expected to be neither null nor "")
	 * @return the parsed value
	 * @throws NumberFormatException
	 */
	@Override
	public Double parse(String value) throws NumberFormatException
	{
		return Double.valueOf(value);
	}

	@Override
	protected void validate(Double value) throws InvalidValueException
	{
		if(!signed && value < 0.0d)
			throw new InvalidValueException("Cannot store negative values because column is unsigned", this);
		/*
		 * Note: I originally planned to also check whether the value could fit in a 32 bit float when in
		 * 		 single precision mode, but it seems there is no obvious (or even correct) way to do this. 
		 */
	}

	@Override
	protected void write(Double value, BitOutputStream bitStream, boolean lossless) throws IOException
	{
		if(doublePrecision || lossless)
			// write as double (64 bits):
			bitStream.write(value);
		else
			// write as float (32 bits):
			bitStream.write(value.floatValue());
	}

	@Override
	protected Double read(BitInputStream bitStream, boolean lossless) throws IOException
	{
		return (doublePrecision || lossless) ?
			// read as double (64 bits):
			bitStream.readDouble() :
			// read as float (32 bits):
			bitStream.readFloat();
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.storage.model.Column#canBeLossy()
	 */
	@Override
	public boolean canBeLossy()
	{
		return !doublePrecision; // more efficient than Column#canBeLossy()
	}

	@Override
	protected int getMinimumValueSize(boolean lossless)
	{
		return doublePrecision || lossless ? Double.SIZE : Float.SIZE;
	}
	
	@Override
	protected int getMaximumValueSize(boolean lossless)
	{
		return getMinimumValueSize(lossless); // size is constant
	}

	@Override
	public String toString(Double value)
	{
		return value.toString();
	}

	@Override
	protected boolean equalRestrictions(Column<Double> otherColumn)
	{
		if(otherColumn instanceof FloatColumn)
		{
			FloatColumn other = (FloatColumn) otherColumn;
			return 	this.doublePrecision == other.doublePrecision &&
					this.signed == other.signed;
		}
		return false;
	}

	@Override
	protected Double copy(Double value)
	{
		return Double.valueOf(value);
	}
	
	/**
	 * Even though the type is actually Double we have called this column a "FloatColumn" 
	 * 
	 * @see uk.ac.ucl.excites.sapelli.storage.model.Column#getTypeString()
	 */
	@Override
	public String getTypeString()
	{
		return Float.class.getSimpleName();
	}
	
	@Override
	public void accept(ColumnVisitor visitor)
	{
		visitor.visit(this);
	}

	@Override
	protected int compareNonNullValues(Double lhs, Double rhs)
	{
		return lhs.compareTo(rhs);
	}
	
	@Override
	public int hashCode()
	{
		int hash = super.hashCode();
		hash = 31 * hash + (doublePrecision ? 0 : 1);
		hash = 31 * hash + (signed ? 0 : 1);
		return hash;
	}

	/**
	 * @return the doublePrecision
	 */
	public boolean isDoublePrecision()
	{
		return doublePrecision;
	}

	/**
	 * @return the signed
	 */
	public boolean isSigned()
	{
		return signed;
	}

	@Override
	public Class<Double> getType()
	{
		return Double.class;
	}
	
}
