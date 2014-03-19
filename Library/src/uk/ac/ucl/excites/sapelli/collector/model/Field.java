package uk.ac.ucl.excites.sapelli.collector.model;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import uk.ac.ucl.excites.sapelli.collector.control.Controller;
import uk.ac.ucl.excites.sapelli.collector.ui.CollectorUI;
import uk.ac.ucl.excites.sapelli.collector.ui.FieldUI;
import uk.ac.ucl.excites.sapelli.storage.model.Column;
import uk.ac.ucl.excites.sapelli.util.CollectionUtils;

/**
 * @author mstevens
 *
 */
/**
 * @author mstevens
 *
 */
public abstract class Field implements JumpSource
{
	
	//Statics----------------------------------------------
	static public enum Optionalness
	{
	    ALWAYS,
	    NOT_IF_REACHED,
	    NEVER
	}
	
	//Defaults:
	static public final boolean DEFAULT_SHOW_BACK = true;
	static public final boolean DEFAULT_SHOW_CANCEL = true;
	static public final boolean DEFAULT_SHOW_FORWARD = true;
	static public final boolean DEFAULT_ENABLED = true;
	static public final boolean DEFAULT_SKIP_ON_BACK = false;
	static public final boolean DEFAULT_SHOW_ON_CREATE = true;
	static public final boolean DEFAULT_SHOW_ON_EDIT = true;
	static public final Optionalness DEFAULT_OPTIONAL = Optionalness.NOT_IF_REACHED;
	static public final boolean DEFAULT_NO_COLUMN = false;
	static public final String DEFAULT_BACKGROUND_COLOR = "#FFFFFF"; //white
	
	
	//Dynamics---------------------------------------------
	protected String id;
	protected String label;
	protected Form form;
	protected Field jump;
	protected boolean enabled = DEFAULT_ENABLED;
	protected boolean skipOnBack = DEFAULT_SKIP_ON_BACK;
	protected boolean showOnCreate = DEFAULT_SHOW_ON_CREATE;
	protected boolean showOnEdit = DEFAULT_SHOW_ON_EDIT;
	protected Optionalness optional = DEFAULT_OPTIONAL;
	protected boolean noColumn = DEFAULT_NO_COLUMN;
	protected String backgroundColor = DEFAULT_BACKGROUND_COLOR;
	
	// Buttons:
	private boolean showBack = DEFAULT_SHOW_BACK;
	private boolean showCancel = DEFAULT_SHOW_CANCEL;
	private boolean showForward = DEFAULT_SHOW_FORWARD;
	
	public Field(Form form, String id)
	{
		this(form, id, null);
	}
	
	public Field(Form form, String id, String label)
	{
		if(id == null || id.trim().isEmpty())
			throw new NullPointerException("ID cannot be null or empty.");
		this.form = form;
		this.id = id.trim();
		this.label = label;
	}
	
	/**
	 * @return the id
	 */
	public String getID()
	{
		return id;
	}
	
	/**
	 * @return the label
	 */
	public String getLabel()
	{
		return label;
	}
	
	/**
	 * @return the noColumn
	 */
	public boolean isNoColumn()
	{
		return noColumn;
	}

	/**
	 * @param noColumn the noColumn to set
	 */
	public void setNoColumn(boolean noColumn)
	{
		this.noColumn = noColumn;
	}

	/**
	 * @return the optional
	 */
	public Optionalness getOptional()
	{
		return optional;
	}

	/**
	 * @param optional the optional to set
	 */
	public void setOptional(Optionalness optionalness)
	{
		this.optional = optionalness;
	}

	/**
	 * @return the skipOnBack
	 */
	public boolean isSkipOnBack()
	{
		return skipOnBack;
	}

	/**
	 * @param skipOnBack the skipOnBack to set
	 */
	public void setSkipOnBack(boolean skipOnBack)
	{
		this.skipOnBack = skipOnBack;
	}
	
	/**
	 * @return the showOnCreate
	 */
	public boolean isShowOnCreate()
	{
		return showOnCreate;
	}

	/**
	 * @param showOnCreate the showOnCreate to set
	 */
	public void setShowOnCreate(boolean showOnCreate)
	{
		this.showOnCreate = showOnCreate;
	}

	/**
	 * @return the showOnEdit
	 */
	public boolean isShowOnEdit()
	{
		return showOnEdit;
	}

	/**
	 * @param showOnEdit the showOnEdit to set
	 */
	public void setShowOnEdit(boolean showOnEdit)
	{
		this.showOnEdit = showOnEdit;
	}

	public void setJump(Field target)
	{
		this.jump = target;
	}
	
	public Field getJump()
	{
		return jump;
	}
	
	/**
	 * @return the enabled
	 */
	public boolean isEnabled()
	{
		return enabled;
	}
	
	public void disable()
	{
		enabled = false;
	}
	
	public void enable()
	{
		enabled = true;
	}
	
	/**
	 * @return the backgroundColor
	 */
	public String getBackgroundColor()
	{
		return backgroundColor;
	}

	/**
	 * @param backgroundColor the backgroundColor to set
	 */
	public void setBackgroundColor(String backgroundColor)
	{
		this.backgroundColor = backgroundColor;
	}

	/**
	 * @return the showBack
	 */
	public boolean isShowBack()
	{
		return showBack;
	}

	/**
	 * @param showBack the showBack to set
	 */
	public void setShowBack(boolean showBack)
	{
		this.showBack = showBack;
	}

	/**
	 * @return the showCancel
	 */
	public boolean isShowCancel()
	{
		return showCancel;
	}

	/**
	 * @param showCancel the showCancel to set
	 */
	public void setShowCancel(boolean showCancel)
	{
		this.showCancel = showCancel;
	}

	/**
	 * @return the showForward
	 */
	public boolean isShowForward()
	{
		return showForward;
	}

	/**
	 * @param showForward the showForward to set
	 */
	public void setShowForward(boolean showForward)
	{
		this.showForward = showForward;
	}

	public List<Column<?>> getColumns()
	{
		if(!noColumn)
			return createColumns();
		return null;
	}
	
	/**
	 * Provided such that RelationField classes (which need to generate multiple columns) can override it
	 * 
	 * @return
	 */
	protected List<Column<?>> createColumns()
	{
		List<Column<?>> cols = new ArrayList<Column<?>>();
		CollectionUtils.addIgnoreNull(cols, createColumn());
		return cols;
	}
	
	/**
	 * Returns a new Column object capable of storing values for this field
	 * Important: there is typically only one column and in that case it is assumed that the field.id is used as the column name.
	 * 
	 * @return
	 */
	protected abstract Column<?> createColumn();
	
	/**
	 * Meant to be overridden in (some) subclasses
	 * 
	 * @return the root Field of this Field
	 */
	public Field getRoot()
	{
		return this;
	}
	
	/**
	 * Meant to be overridden in (some) subclasses
	 * 
	 * @return whether or not this is a root Field
	 */
	public boolean isRoot()
	{
		return true;
	}
	
	/**
	 * @return the form
	 */
	public Form getForm()
	{
		return form;
	}

	/**
	 * To be overriden by Fields that use files (images, sounds, etc.) that are stored with the project
	 * 
	 * @param project
	 * @return
	 */
	public List<File> getFiles(Project project)
	{
		return null;
	}
	
	/**
	 * Called to signal that the user is entering the field. This allows any required initialisation behaviour to be carried out.
	 * The returned boolean indicates whether or not a UI update is required after entering the field.
	 * 
	 *  This method uses double-dispatch: the actual Field-type-specific behaviour will be defined in the class implementing the Controller interface.
	 * 
	 * @param controller
	 * @return whether or not a UI update is required after entering the field)
	 */
	public abstract boolean enter(Controller controller);
	
	/**
	 * Returns a FieldUI object to represent this Field.
	 * 
	 * This method uses double-dispatch: the actual FieldUI object will be instantiated by the class implementing the CollectorUI interface.
	 * 
	 * @param collectorUI
	 * @return
	 */
	public abstract FieldUI createUI(CollectorUI collectorUI);
	
}