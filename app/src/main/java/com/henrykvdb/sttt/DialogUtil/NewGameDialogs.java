package com.henrykvdb.sttt.DialogUtil;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import com.henrykvdb.sttt.Util.AcceptCallback;
import com.henrykvdb.sttt.Util.Callback;
import com.henrykvdb.sttt.GameState;
import com.henrykvdb.sttt.MMBot;
import com.henrykvdb.sttt.R;

import java.util.Random;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;

public class NewGameDialogs
{
	public static void newLocal(AcceptCallback callback, Context context)
	{
		DialogInterface.OnClickListener dialogClickListener = (dialog, which) ->
		{
			switch (which)
			{
				case DialogInterface.BUTTON_POSITIVE:
					callback.accept();
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					dialog.dismiss();
					break;
			}
		};

		BasicDialogs.keepDialog(new AlertDialog.Builder(context)
				.setTitle("Start a new game?")
				.setMessage("This wil create a new local two player game.")
				.setPositiveButton("Start", dialogClickListener)
				.setNegativeButton("Close", dialogClickListener)
				.show());
	}

	public static void newAi(Callback<GameState> callback, Activity activity)
	{
		final boolean[] swapped = new boolean[1];

		LayoutInflater inflater = (LayoutInflater) activity.getSystemService(LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.dialog_ai, (ViewGroup) activity.findViewById(R.id.new_ai_layout));

		RadioGroup beginner = (RadioGroup) layout.findViewById(R.id.start_radio_group);
		beginner.setOnCheckedChangeListener((group, checkedId) ->
				swapped[0] = checkedId != R.id.start_you && (checkedId == R.id.start_ai || new Random().nextBoolean()));

		DialogInterface.OnClickListener dialogClickListener = (dialog, which) ->
		{
			switch (which)
			{
				case DialogInterface.BUTTON_POSITIVE:
					callback.callback(GameState.builder()
							.ai(new MMBot(((SeekBar) layout.findViewById(R.id.difficulty)).getProgress()))
							.swapped(swapped[0]).build());
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					dialog.dismiss();
					break;
			}
		};

		BasicDialogs.keepDialog(new AlertDialog.Builder(activity)
				.setView(layout)
				.setTitle("Start a new ai game?")
				.setPositiveButton("Start", dialogClickListener)
				.setNegativeButton("Close", dialogClickListener)
				.show());
	}

	public void btPicker(Callback<String> callback, Activity activity)
	{
		final boolean[] swapped = new boolean[1];

		LayoutInflater inflater = (LayoutInflater) activity.getSystemService(LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.dialog_ai, (ViewGroup) activity.findViewById(R.id.new_ai_layout));

		RadioGroup beginner = (RadioGroup) layout.findViewById(R.id.start_radio_group);
		beginner.setOnCheckedChangeListener((group, checkedId) ->
				swapped[0] = checkedId != R.id.start_you && (checkedId == R.id.start_ai || new Random().nextBoolean()));

		DialogInterface.OnClickListener dialogClickListener = (dialog, which) ->
		{
			switch (which)
			{
				case DialogInterface.BUTTON_POSITIVE:
					//callback.callback(); //TODO CALLBACK
					break;

				case DialogInterface.BUTTON_NEGATIVE:
					dialog.dismiss();
					break;
			}
		};


		//Create custom title
		TypedValue outValue = new TypedValue();
		activity.getTheme().resolveAttribute(R.attr.alertDialogTheme, outValue, true);
		TextView tv = new TextView(activity,null,outValue.resourceId);
		tv.setText("This is a title");

		BasicDialogs.keepDialog(new AlertDialog.Builder(activity)
				.setView(layout)
				.setTitle(Html.fromHtml("<font color='#000000'>Start a new ai game?</font>"))
				//.setCustomTitle(tv)
				.setPositiveButton("Start", dialogClickListener)
				.setNegativeButton("Close", dialogClickListener)
				.show());
	}
}