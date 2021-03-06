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

package uk.ac.ucl.excites.sapelli.collector.ui.fields;

import uk.ac.ucl.excites.sapelli.collector.R;
import uk.ac.ucl.excites.sapelli.collector.control.AndroidCollectorController;
import uk.ac.ucl.excites.sapelli.collector.control.CollectorController.Mode;
import uk.ac.ucl.excites.sapelli.collector.model.fields.TextBoxField;
import uk.ac.ucl.excites.sapelli.collector.model.fields.TextBoxField.Content;
import uk.ac.ucl.excites.sapelli.collector.ui.CollectorView;
import uk.ac.ucl.excites.sapelli.collector.ui.DelKeyEventEditText;
import uk.ac.ucl.excites.sapelli.collector.ui.drawables.DiagonalCross;
import uk.ac.ucl.excites.sapelli.collector.util.ScreenMetrics;
import uk.ac.ucl.excites.sapelli.shared.util.android.ViewHelpers;
import uk.ac.ucl.excites.sapelli.storage.model.Record;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * @author Julia, mstevens
 * 
 */
public class AndroidTextBoxUI extends TextBoxUI<View, CollectorView>
{

	// STATIC -------------------------------------------------------	
	private static final float NULL_MODE_CROSS_LINE_WIDTH_DIP = 5.0f;
	private static final int NULL_MODE_CROSS_COLOR = Color.LTGRAY;
	private static final int NULL_MODE_TEXT_COLOR = Color.DKGRAY;
	
	// DYNAMIC ------------------------------------------------------
	private TextBoxView view;

	public AndroidTextBoxUI(TextBoxField textBox, AndroidCollectorController controller, CollectorView collectorView)
	{
		super(textBox, controller, collectorView);
	}

	@Override
	protected String getValue()
	{
		if(view == null)
			return null; // this shouldn't happen
		return view.getValue();
	}

	@Override
	protected View getPlatformView(boolean onPage, boolean enabled, Record record, boolean newRecord)
	{
		// Create view if needed:
		if(view == null)
		{
			view = new TextBoxView(collectorUI.getContext());
			newRecord = true; // force update of new view
		}

		// Disable when in edit mode and field is not editable, otherwise enable:
		view.setEnabled(enabled); // also sets event handlers

		// Update view:
		if(newRecord)
		{
			// Clear error:
			view.clearError();

			// Set default or current value:
			String value = retrieveValue(record);
			view.setValue(value != null || (field.isOptional() && controller.getCurrentMode() == Mode.EDIT) ? value : field.getInitialValue());
		}

		// Return view:
		return view;
	}

	@Override
	protected void setValidationError(int errorCode, Integer argument, boolean hintOptional)
	{
		if(view == null)
			return; // !!!
		String errorDescr = null;
		switch(errorCode)
		{
			case VALIDATION_ERROR_NON_OPTIONAL_MISSING :
				errorDescr = collectorUI.getContext().getString(R.string.txtValidationErrorMissing);
				break;
			case VALIDATION_ERROR_TOO_SHORT :
				errorDescr = collectorUI.getContext().getString(R.string.txtValidationErrorShort, argument);
				break;
			case VALIDATION_ERROR_TOO_LONG :
				errorDescr = collectorUI.getContext().getString(R.string.txtValidationErrorLong, argument);
				break;
			case VALIDATION_ERROR_PATTERN_MISMATCH :
				errorDescr = collectorUI.getContext().getString(R.string.txtValidationErrorrPattern);
				break;
			case VALIDATION_ERROR_INVALID_NUMERIC :
				errorDescr = collectorUI.getContext().getString(R.string.txtValidationErrorInvalidNumber);
				break;
			case VALIDATION_ERROR_INVALID :
			default:
				errorDescr = collectorUI.getContext().getString(R.string.txtValidationErrorInvalid);
				break;
		}
		
		// Update UI, from the main/UI thread:
		final String finalErrorDescr = errorDescr;
		collectorUI.activity.runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				view.setError(finalErrorDescr);
			}
		});
	}

	@Override
	protected void clearValidationError()
	{
		if(view != null)
			// Use the main/UI thread:
			collectorUI.activity.runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					view.clearError();
				}
			});
	}
	
	/* (non-Javadoc)
	 * @see uk.ac.ucl.excites.sapelli.collector.ui.fields.FieldUI#claimFocus()
	 */
	@Override
	public boolean claimFocus()
	{
		if(isFieldShown() && view != null && view.isEnabled() && !view.nullMode)
		{
			view.editText.requestFocus();
			collectorUI.showKeyboardFor(view.editText);
			return true;
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see uk.ac.ucl.excites.sapelli.collector.ui.FieldUI#cancel()
	 */
	@Override
	protected void cancel()
	{
		collectorUI.hideKeyboard();
	}

	private class TextBoxView extends LinearLayout implements OnFocusChangeListener, OnKeyListener, TextWatcher
	{

		private EditText editText;
		private TextView errorMsg;
		private boolean watchText = true;
		private boolean nullMode = false;
		private Drawable crossDrawable = null;
		
		// Variables to hold on to some of editText's default attributes while in nullMode: 
		private Drawable editTextBackground = null;
		private ColorStateList editTextColors = null;
		private int editTextGravity = ViewHelpers.getStartGravity();
		
		/**
		 * @param context
		 * 
		 */
		public TextBoxView(Context context)
		{
			super(context);

			setOrientation(LinearLayout.VERTICAL);
			setLayoutParams(CollectorView.FULL_WIDTH_LAYOUTPARAMS);

			// Label:
			if (field.hasCaption())
			{
				TextView label = new TextView(context);
				label.setText(field.getCaption());
				label.setLayoutParams(CollectorView.FULL_WIDTH_LAYOUTPARAMS);
				addView(label);
			}
			
			// Textbox:
			editText = new DelKeyEventEditText(context);
			editText.setLayoutParams(CollectorView.FULL_WIDTH_LAYOUTPARAMS);
			setInputConstraints(field);
			// Add the textbox:
			addView(editText);

			// Error msg:
			errorMsg = new TextView(context);
			errorMsg.setLayoutParams(CollectorView.FULL_WIDTH_LAYOUTPARAMS);
			errorMsg.setTextColor(Color.RED);
			errorMsg.setGravity(ViewHelpers.getEndGravity());
			errorMsg.setTextSize(TypedValue.COMPLEX_UNIT_PX, errorMsg.getTextSize() * 0.9f);
			errorMsg.setVisibility(GONE);
			addView(errorMsg);
		}

		/**
		 * @param value the text to set (may be null)
		 */
		public void setValue(String value)
		{
			// Set text or enable nullMode:
			if(value != null)
			{	
				// Briefly disable text watching:
				watchText = false;
				
				setNullMode(false); // won't do anything if nullMode=false
				editText.setText(value);
				editText.setSelection(value.length()); // set cursor to end
				
				// Re-enable text watching:
				watchText = true;
			}
			else
				setNullMode(true);
		}
		
		public String getValue()
		{
			if(nullMode)
				return null;
			return editText.getText().toString();
		}
		
		/**
		 * @param enable
		 * @return whether null mode was actually enabled/disabled
		 */
		private boolean setNullMode(boolean enable)
		{
			// Enable nullMode:
			if(enable && !nullMode)
			{
				// change mode:
				nullMode = true;
				// disable all input constraints:
				setInputConstraints(null);
				// remember current gravity & set to center:
				editTextGravity = editText.getGravity();
				editText.setGravity(Gravity.CENTER);
				if(isEnabled())
				{
					// remember current background drawable & set "crossed" one:
					editTextBackground = editText.getBackground();
					if(crossDrawable == null)
						crossDrawable = new DiagonalCross(NULL_MODE_CROSS_COLOR, ScreenMetrics.ConvertDipToPx(getContext(), NULL_MODE_CROSS_LINE_WIDTH_DIP), Paint.Cap.ROUND);
					ViewHelpers.setViewBackground(editText, new LayerDrawable(new Drawable[] { editTextBackground, crossDrawable }));
					// remember current text colors & set color to dark gray:
					editTextColors = editText.getTextColors();
					editText.setTextColor(NULL_MODE_TEXT_COLOR);
					// Set "Touch to set/edit" String:
					editText.setText(controller.getCurrentMode() == Mode.CREATE ? R.string.txtNullModeCreate : R.string.txtNullModeEdit);
				}
				else
					// Set "(no value set)" String:
					editText.setText(R.string.txtNullModeDisabled);
				// Lose focus:
				editText.clearFocus();
				// Null mode enabled:
				return true;
			}
			// Disable nullMode:
			else if(!enable && nullMode && isEnabled())
			{
				// (re)enable input constraints for field
				setInputConstraints(field);
				// reset gravity:
				editText.setGravity(editTextGravity);
				// reset background drawable:
				ViewHelpers.setViewBackground(editText, editTextBackground);
				// reset text colors:
				editText.setTextColor(editTextColors);
				// make textbox empty:
				editText.setText("");
				// change mode:
				nullMode = false;
				// Null mode disabled:
				return true;
			}
			// No change:
			return false;
		}
		
		/**
		 * @param field the field or null if in nullMode
		 */
		private void setInputConstraints(TextBoxField field)
		{
			// Multiline (as specified as part of input type);
			editText.setSingleLine(field != null ? !field.isMultiline() : true);
			// Limit input length to specified maximum:
			editText.setFilters(field != null ? new InputFilter[] { new InputFilter.LengthFilter(field.getMaxLength()) } : new InputFilter[] { });
			// Set input type:
			editText.setInputType(field != null ?
									getInputType() :
									InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); // plain text without spell check
		}
		
		@Override
		public void onFocusChange(View v, boolean hasFocus)
		{
			if(!isFieldShown() || !isEnabled())
				return;
			
			if(!hasFocus)
			{ 	// Focus is lost, so...
				// Hide keyboard if it is currently shown:
				collectorUI.hideKeyboard();
				// Validate unless in nullMode:
				if(!nullMode) // letting validation happen in nullMode wouldn't actually cause errors (getText() would return null, which would be valid), but it is unnecessary
					isValidInformPage(controller.getCurrentRecord()); // will call isValid() but via the containing page such that the red box can (dis)appear, if the field is not on a page isValid() is called directly
			}
			else if(nullMode)
				/* Note:
				 * 	We use a post() here because if we call setNullMode(false) directly here the keyboard
				 * 	doesn't appear on Android v2.3.x (observed on Xcover1/v2.3.6). However, the downside
				 *	of this solution is that on more recent versions (observed on Nexus4/v4.4.2, but not on
				 *	Xcover1/v2.3.6) it triggers "getTextBeforeCursor on inactive InputConnection" warnings
				 *	appearing in logcat. We could of course make the use of post() depend on the Android
				 *	version but so far we don't know exactly which version(s) are affected by this side-effect.
				 *	So given that these warnings are harmless (only appearing in logcat and no functionality
				 *	issues) we will just use post() unconditionally (at least for now).
				 */
				post(new Runnable()
				{
					@Override
					public void run()
					{
						// disable nullMode on touch:
						setNullMode(false);
					}
				});
		}
		
		@Override
		public void setEnabled(boolean enabled)
		{
			super.setEnabled(enabled);
			editText.setEnabled(enabled);
			// Event handlers:
			editText.setOnFocusChangeListener(enabled ? this : null);
			editText.addTextChangedListener(enabled ? this : null);
			editText.setOnKeyListener(enabled && field.isOptional() ? this : null); // only used on optional fields!!
		}
		
		/**
		 * Typing backspace on empty text field will activate null mode.
		 * Only used when field is always optional.
		 * 
		 * @see android.view.View.OnKeyListener#onKey(android.view.View, int, android.view.KeyEvent)
		 */
		@Override
		public boolean onKey(View v, int keyCode, KeyEvent event)
		{
			if(keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN && getValue().isEmpty())
				return setNullMode(true);
			return false;
		}

		@Override
		final public void beforeTextChanged(CharSequence s, int start, int count, int after)
		{
			if(isFieldShown() && isEnabled() && watchText)
			{	// the user is currently typing (or nullMode is being enabled/disabled), so don't annoy him/her with the red box and error msg:
				clearPageInvalidMark(); // does nothing when field is not on a page
				clearError();
			}
		}

		@Override
		final public void onTextChanged(CharSequence s, int start, int before, int count)
		{
			/* Don't care */
		}
		
		@Override
		final public void afterTextChanged(Editable s)
		{
			/* Don't care */
		}
		
		public void setError(String error)
		{
			if(error == null || error.isEmpty())
			{	// just to be sure
				clearError();
				return;
			}
			errorMsg.setText(error);
			errorMsg.setVisibility(VISIBLE);
		}

		public void clearError()
		{
			errorMsg.setText("");
			errorMsg.setVisibility(GONE);
		}

	}

	/**
	 * @return integer representing input type
	 * 
	 * @see <a href="http://developer.android.com/reference/android/text/InputType.html">InputType</a>
	 * @see <a href="http://developer.android.com/reference/android/widget/TextView.html#attr_android:inputType">TextView inputType</a>
	 */
	private int getInputType()
	{
		int inputType;

		// Input Class:
		switch(field.getContent())
		{
		case text:
		case email:
		case password:
		default:
			// Text class:
			inputType = InputType.TYPE_CLASS_TEXT;
			break;
		case phonenumber:
			// Phone class:
			inputType = InputType.TYPE_CLASS_PHONE;
			break;
		case unsignedint:
		case signedint:
		case unsignedlong:
		case signedlong:
		case unsignedfloat:
		case signedfloat:
		case unsigneddouble:
		case signeddouble:
			// Number class:
			inputType = InputType.TYPE_CLASS_NUMBER;
			break;
		}

		// Variations:
		switch(field.getContent())
		{
		case text:
		default:
			inputType |= InputType.TYPE_TEXT_VARIATION_NORMAL;
			break;
		case email:
			inputType |= InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
			break;
		case password:
			inputType |= InputType.TYPE_TEXT_VARIATION_PASSWORD;
			break;
		case phonenumber:
			break;
		case unsignedint:
		case unsignedlong:
			break;
		case signedint:
		case signedlong:
			inputType |= InputType.TYPE_NUMBER_FLAG_SIGNED;
			break;
		case unsignedfloat:
		case unsigneddouble:
			inputType |= InputType.TYPE_NUMBER_FLAG_DECIMAL;
			break;
		case signedfloat:
		case signeddouble:
			inputType |= InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL;
			break;
		}

		// Only for content=text:
		if(field.getContent() == Content.text)
		{
			// Automatic capitalisation:
			switch(field.getCapitalisation())
			{
			case none:
			default:
				break;
			case all:
				inputType |= InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS;
				break;
			case words:
				inputType |= InputType.TYPE_TEXT_FLAG_CAP_WORDS;
				break;
			case sentences:
				inputType |= InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
				break;
			}

			// Multi-line:
			if(field.isMultiline())
				inputType |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
		}

		return inputType;
	}

}
