/**
 * 
 */
package uk.ac.ucl.excites.sapelli.collector.model.fields;

import uk.ac.ucl.excites.sapelli.collector.control.Controller;
import uk.ac.ucl.excites.sapelli.collector.model.FieldParameters;
import uk.ac.ucl.excites.sapelli.collector.model.Form;
import uk.ac.ucl.excites.sapelli.collector.xml.FormParser;
import uk.ac.ucl.excites.sapelli.storage.model.Column;

/**
 * A relationship from one form to another which constitutes a "jump"</br>
 *	The relationship between the forms is purely "navigational" and there is no stored association between their records.
 *	In this case the Relation field merely provides a "passage way" through which navigation to the other form is possible.
 *	An "intra-form" jump to the relation field will automatically result in a subsequent "inter-form" jump to the {@code relatedForm}
 *
 * @author mstevens
 */
public class LinksToField extends Relationship
{

	/**
	 * @param form
	 * @param id
	 */
	public LinksToField(Form form, String id)
	{
		super(form, id);
		this.noColumn = true;
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.collector.project.model.Field#createColumn()
	 */
	@Override
	protected Column<?> createColumn()
	{	
		return null;
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.collector.model.Field#enter(uk.ac.ucl.excites.sapelli.collector.control.Controller, uk.ac.ucl.excites.sapelli.collector.model.FieldParameters, boolean)
	 */
	@Override
	public boolean enter(Controller controller, FieldParameters arguments, boolean withPage)
	{
		return controller.enterLinksTo(this, arguments);
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true; // references to same object
		if(obj instanceof BelongsToField)
			return super.equals(obj); // Relationship#equals(Object)
		else
			return false;
	}
	
	@Override
	public int hashCode()
	{
		int hash = super.hashCode(); // Relationship#hashCode()
		hash = 31 * hash + FormParser.TAG_LINKS_TO.hashCode();
		return hash;
	}
	
}
