/**
 * 
 */
package uk.ac.ucl.excites.sapelli.collector.model.fields;

import java.util.ArrayList;
import java.util.List;

import uk.ac.ucl.excites.sapelli.collector.control.Controller;
import uk.ac.ucl.excites.sapelli.collector.model.Field;
import uk.ac.ucl.excites.sapelli.collector.model.FieldParameters;
import uk.ac.ucl.excites.sapelli.collector.model.Form;
import uk.ac.ucl.excites.sapelli.collector.model.dictionary.Dictionary;
import uk.ac.ucl.excites.sapelli.collector.model.dictionary.Dictionary.DictionarySerialiser;
import uk.ac.ucl.excites.sapelli.collector.model.dictionary.DictionaryItem;
import uk.ac.ucl.excites.sapelli.collector.ui.CollectorUI;
import uk.ac.ucl.excites.sapelli.collector.ui.fields.MultiListUI;
import uk.ac.ucl.excites.sapelli.storage.model.columns.IntegerColumn;
import uk.ac.ucl.excites.sapelli.storage.model.columns.StringColumn;
import uk.ac.ucl.excites.sapelli.storage.util.StringListMapper;

/**
 * Field that allows users to select a value from a hierarchy presented as multiple listboxes
 * 
 * @author mstevens
 */
public class MultiListField extends Field
{

	static public final String UNKNOWN_LABEL_PREFIX = "Level"; //TODO multilang
	static public final boolean DEFAULT_PRESELECT = true;
	static public final String CAPTION_SEPARATOR = ";";
	
	private String[] captions;
	private MultiListItem itemsRoot;
	private boolean preSelect = DEFAULT_PRESELECT;
	private Dictionary<MultiListItem> values;
	
	/**
	 * @param form
	 * @param id
	 * @param captions
	 */
	public MultiListField(Form form, String id, String captions)
	{
		super(form, id);
		this.captions = captions.split(CAPTION_SEPARATOR);
		this.itemsRoot = new MultiListItem(this);
		this.values = new Dictionary<MultiListItem>();
	}
	
	@Override
	public String getCaption()
	{
		return getCaption(0);
	}
	
	public String getCaption(int level)
	{
		if(level < 0)
			throw new IndexOutOfBoundsException("Level cannot be negative!");
		else if(level < captions.length)
			return captions[level];
		else
			return captions[0].isEmpty() ? "" : UNKNOWN_LABEL_PREFIX + level;
	}

	/**
	 * @return the preSelect
	 */
	public boolean isPreSelect()
	{
		return preSelect;
	}

	/**
	 * @param preSelect the preSelect to set
	 */
	public void setPreSelect(boolean preSelect)
	{
		this.preSelect = preSelect;
	}

	public MultiListItem getItemsRoot()
	{
		return itemsRoot;
	}
	
	/**
	 * @return the values dictionary
	 */
	public Dictionary<MultiListItem> getDictionary()
	{
		return values;
	}
	
	public int getValueForItem(MultiListItem item)
	{
		return values.lookupIndex(item);
	}
	
	public MultiListItem getItemForValue(int value)
	{
		return values.lookupItem(value);
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.collector.project.model.Field#createColumn()
	 */
	@Override
	protected IntegerColumn createColumn()
	{
		// Initialise dictionary:
		addLeaves(itemsRoot); // depth-first traversal
		// Column...
		if(values.isEmpty())
		{	//no values set
			noColumn = true; //!!!
			form.addWarning("noColumn was forced to true on MultiListField " + getID() + " because it has no items.");
			return null;
		}
		else
		{
			boolean opt = (optional != Optionalness.NEVER);
			
			//Create column:
			IntegerColumn col = new IntegerColumn(id, opt, 0, values.size() - 1);
			
			// Add virtual columns to it:
			//	Find maximum level:
			int maxLevel = 0;
			for(MultiListItem item : values.getItems())
			{
				int itemLevel = item.getLevel(); 
				if(itemLevel > maxLevel)
					maxLevel = itemLevel;
			}
			//	A String value column for each level: 
			for(int l = 0; l <= maxLevel; l++)
			{
				final int level = l;
				//	Value String column:
				StringListMapper levelValueMapper = new StringListMapper(values.serialise(new DictionarySerialiser<MultiListItem>()
				{
					@Override
					public String serialise(MultiListItem item)
					{
						MultiListItem parentAtLevel = item.getParentAt(level);
						return parentAtLevel != null ? parentAtLevel.value : null;
					}
				}));
				col.addVirtualVersion(StringColumn.ForCharacterCount(getCaption(level), opt, Math.max(levelValueMapper.getMaxStringLength(), 1)), levelValueMapper); // TODO ensure no illegal chars are in caption
			}

			// Return the column:
			return col;
		}
	}
	
	private void addLeaves(MultiListItem item)
	{
		if(item.isLeaf())
			values.addItem(item);
		else
			for(MultiListItem c : item.getChildren())
				addLeaves(c);
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.collector.model.Field#enter(uk.ac.ucl.excites.sapelli.collector.control.Controller, boolean)
	 */
	@Override
	public boolean enter(Controller controller, FieldParameters arguments, boolean withPage)
	{
		return true;
	}

	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.collector.project.model.Field#createUI(uk.ac.ucl.excites.collector.project.ui.CollectorUI)
	 */
	@Override
	public <V, UI extends CollectorUI<V, UI>> MultiListUI<V, UI> createUI(UI collectorUI)
	{
		return collectorUI.createMultiListUI(this);
	}
	
	@Override
	public IntegerColumn getColumn()
	{
		return (IntegerColumn) super.getColumn();
	}
	
	/**
	 * A class representing items in the MultiListField
	 * 
	 * @author mstevens
	 */
	public static class MultiListItem implements DictionaryItem
	{
		
		static public final int NO_DEFAULT_ITEM_SET_IDX = -1; 

		static public MultiListItem GetDummyItem(MultiListField field, String value)
		{
			MultiListItem dummy = new MultiListItem(field);
			dummy.value = value;
			return dummy;
		}
		
		private MultiListField field;
		private MultiListItem parent;
		
		private String value;
		
		private List<MultiListItem> children = new ArrayList<MultiListItem>();
		private int defaultChildIdx = NO_DEFAULT_ITEM_SET_IDX;

		/**
		 * Only for root item held directly by MultiListField
		 * 
		 * @param field
		 */
		/*package*/ MultiListItem(MultiListField field)
		{
			this.field = field;
			// parent & value stay null
			// children list initialised above
		}
		
		public MultiListItem(MultiListItem parent, String value)
		{
			if(parent == null)
				throw new IllegalArgumentException("Parent cannot be null");
			this.parent = parent;
			parent.addChild(this); //!!!
			this.field = parent.field;
			this.value = value;
			// children list initialised above
		}
		
		/**
		 * @return the value
		 */
		public String getValue()
		{
			return value;
		}
		
		public String toString()
		{
			return value;
		}
		
		public void addChild(MultiListItem child)
		{
			children.add(child);
		}
		
		/**
		 * @return the field
		 */
		public MultiListField getField()
		{
			return field;
		}

		/**
		 * @return the parent
		 */
		public MultiListItem getParent()
		{
			return parent;
		}
		
		public boolean isRoot()
		{
			return parent == null;
		}
		
		/**
		 * @return the children
		 */
		public List<MultiListItem> getChildren()
		{
			return children;
		}
		
		public boolean isLeaf()
		{
			return children.isEmpty();
		}
		
		/**
		 * @return the level of the item (levels start at 0 and correspond to captions)
		 */
		public int getLevel()
		{
			if(isRoot())
				return -1; // root doesn't count as an actual level
			else
				return 1 + parent.getLevel();
		}
		
		public MultiListItem getParentAt(int level)
		{
			int myLevel = getLevel();
			if(myLevel == level)
				return this;
			else if(myLevel < level)
				return null;
			else //if(myLevel > level)
				return parent.getParentAt(level); // go up
		}
		
		/**
		 * @return the defaultChild
		 */
		public MultiListItem getDefaultChild()
		{
			if(defaultChildIdx == NO_DEFAULT_ITEM_SET_IDX)
				return null;
			else
				return children.get(defaultChildIdx);
		}
		
		/**
		 * @return the index of the defaultChild
		 */
		public int getDefaultChildIndex()
		{
			return defaultChildIdx;
		}

		/**
		 * @param defaultChild the defaultChild to set
		 */
		public void setDefaultChild(MultiListItem defaultChild)
		{
			int idx = children.indexOf(defaultChild);
			if(idx == -1)
				throw new IllegalArgumentException("Unknown child: " + defaultChild.toString());
			this.defaultChildIdx = idx;
		}

		@Override
		public List<String> getDocExtras()
		{
			return null;
		}

	}
	
}
