package com.henrykvdb.sttt;

import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.ViewDragHelper;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Coord;
import com.flaghacker.uttt.common.Player;
import com.flaghacker.uttt.common.Timer;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.henrykvdb.sttt.Util.Callback;
import com.henrykvdb.sttt.Util.DialogUtil;
import com.henrykvdb.sttt.Util.Util;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static com.flaghacker.uttt.common.Player.ENEMY;
import static com.flaghacker.uttt.common.Player.PLAYER;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener
{
	//Keys
	private static final String GAMESTATE_KEY = "GAMESTATE_KEY";
	private static final String STARTED_WITH_BT_KEY = "STARTED_WITH_BT_KEY";

	//Fields
	private GameState gs;
	private boolean keepBtOn; //If the app started with bluetooth the app won't disable bluetooth

	//Request codes
	public static final int REQUEST_ENABLE_BT = 100;        //Permission required to enable Bluetooth
	public static final int REQUEST_ENABLE_DSC = 101;       //Permission required to enable discoverability
	public static final int REQUEST_COARSE_LOCATION = 102;  //Permission required to search nearby devices

	//Intents
	public static final String CLOSE_BT_SERVICE_INTENT = "close_bt_service_intent";

	//Notification ID's
	private static final int BT_STILL_RUNNING = 1;

	//Bluetooth Service
	private BtService btService;
	private boolean btServiceBound;

	//Bluetooth fields
	private BluetoothAdapter btAdapter;

	//Game fields
	private AtomicReference<Pair<Coord, Source>> playerMove = new AtomicReference<>();
	private final Object playerLock = new Object[0];
	private BoardView boardView;
	private GameThread gameThread;

	//Other
	private Toast toast;
	private AlertDialog askDialog;
	private AlertDialog btDialog;

	//TEMP
	public static String debuglog = "DEBUGLOG";

	public enum Source
	{
		Local,
		AI,
		Bluetooth
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		//Create some variables used later
		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);
		DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);

		//Make it easier to open the drawer
		try
		{
			Field mDragger = drawer.getClass().getDeclaredField("mLeftDragger");
			mDragger.setAccessible(true);
			ViewDragHelper draggerObj = (ViewDragHelper) mDragger.get(drawer);
			Field mEdgeSize = draggerObj.getClass().getDeclaredField("mEdgeSize");
			mEdgeSize.setAccessible(true);
			mEdgeSize.setInt(draggerObj, mEdgeSize.getInt(draggerObj) * 4);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		//Add listener to open and closeGame drawer
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
				this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
		drawer.addDrawerListener(toggle);
		toggle.syncState();

		//Add listener to the items
		NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
		navigationView.setNavigationItemSelectedListener(this);

		//Add ads in portrait
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
		{
			MobileAds.initialize(getApplicationContext(), getString(R.string.banner_ad_unit_id));
			((AdView) findViewById(R.id.adView)).loadAd(new AdRequest.Builder().build());
		}

		//Register receiver to close bt service intent
		IntentFilter filter = new IntentFilter();
		filter.addAction(CLOSE_BT_SERVICE_INTENT);
		registerReceiver(intentReceiver, filter);

		//Prepare fields
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		boardView = (BoardView) findViewById(R.id.boardView);
		boardView.setNextPlayerView((TextView) findViewById(R.id.next_move_view));

		if (savedInstanceState == null)
		{
			//New game
			keepBtOn = btAdapter != null && btAdapter.isEnabled();
			newLocal();
		}
		else
		{
			//Restore game
			keepBtOn = savedInstanceState.getBoolean(STARTED_WITH_BT_KEY, false);
			newGame((GameState) savedInstanceState.getSerializable(GAMESTATE_KEY));
		}

		//Ask the user to rateDialog
		if (savedInstanceState == null)
			DialogUtil.rateDialog(this);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		outState.putBoolean(STARTED_WITH_BT_KEY, keepBtOn);
		outState.putSerializable(GAMESTATE_KEY, gs);
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onStart()
	{
		super.onStart();

		EventBus.getDefault().register(this);
		registerReceiver(btStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

		NotificationManagerCompat notificationManager = NotificationManagerCompat.from(MainActivity.this);
		notificationManager.cancel(BT_STILL_RUNNING);

		if (!btServiceBound)
			bindBtService();
	}

	@Override
	protected void onStop()
	{
		super.onStop();

		EventBus.getDefault().unregister(this);
		unregisterReceiver(btStateReceiver);

		//Notification telling the user that BtService is still open
		if (btService.getState() == BtService.State.CONNECTED)
			btRunningNotification();

		boolean kill = !isChangingConfigurations() && btService.getState() != BtService.State.CONNECTED;
		unbindBtService(kill);
	}

	public void btRunningNotification()
	{
		//This intent reopens the app
		Intent reopenIntent = new Intent(this, MainActivity.class);
		reopenIntent.setAction(Intent.ACTION_MAIN);
		reopenIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		PendingIntent reopenPendingIntent = PendingIntent.getActivity(this, 0, reopenIntent, PendingIntent.FLAG_ONE_SHOT);

		//This intent shuts down the btService
		Intent intentAction = new Intent(CLOSE_BT_SERVICE_INTENT);
		PendingIntent pendingCloseIntent = PendingIntent.getBroadcast(this, 1, intentAction, PendingIntent.FLAG_ONE_SHOT);

		Notification notification = new NotificationCompat.Builder(this)
				.setSmallIcon(R.drawable.ic_icon)
				.setContentTitle("Super Tic Tac Toe")
				.setContentIntent(reopenPendingIntent)
				.setContentText("A Bluetooth game is still running.\nTouch to open STTT")
				.setStyle(new NotificationCompat.BigTextStyle().bigText("A Bluetooth game is still running.\nTouch to open STTT"))
				.addAction(R.drawable.ic_menu_bluetooth, "close", pendingCloseIntent)
				.setOngoing(true).build();

		NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
		notificationManager.notify(BT_STILL_RUNNING, notification);
	}

	private BroadcastReceiver intentReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			unbindBtService(true);
			NotificationManagerCompat notificationManager = NotificationManagerCompat.from(MainActivity.this);
			notificationManager.cancel(BT_STILL_RUNNING);
		}
	};

	@Override
	protected void onDestroy()
	{
		unregisterReceiver(intentReceiver);

		if (!keepBtOn)
			btAdapter.disable();

		NotificationManagerCompat notificationManager = NotificationManagerCompat.from(MainActivity.this);
		notificationManager.cancel(BT_STILL_RUNNING);

		super.onDestroy();
	}

	private void bindBtService()
	{
		if (btServiceBound)
			throw new RuntimeException("BtService already bound");

		if (!Util.isServiceRunning(BtService.class, this))
			startService(new Intent(this, BtService.class));

		bindService(new Intent(this, BtService.class), btServerConn, Context.BIND_AUTO_CREATE);
	}

	private void unbindBtService(boolean stop)
	{
		if (btServiceBound)
		{
			dismissBtDialog();
			unbindService(btServerConn);
			btServiceBound = false;
		}

		if (stop)
		{
			stopService(new Intent(this, BtService.class));
			turnLocal();
		}
	}

	private ServiceConnection btServerConn = new ServiceConnection()
	{
		@Override
		public void onServiceConnected(ComponentName name, IBinder service)
		{
			Log.e("BTS", "btService Connected");

			btService = ((BtService.LocalBinder) service).getService();
			btServiceBound = true;

			if (gs.isBluetooth())
			{
				if (btService.getState() == BtService.State.CONNECTED)
				{
					//Fetch latest board
					Board newBoard = btService.getLocalBoard();
					if (newBoard != gs.board())
						play(newBoard.getLastMove(), Source.Bluetooth);

					//Update subtitle
					setSubTitle("Connected to " + btService.getConnectedDeviceName());
				}
				else turnLocal();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name)
		{
			Log.e("BTS", "btService Disconnected");
			btServiceBound = false;
		}
	};

	private final BroadcastReceiver btStateReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)
					&& btAdapter.getState() == BluetoothAdapter.STATE_TURNING_OFF)
			{
				dismissBtDialog();
				btService.closeThread();
				keepBtOn = false;
				Log.e("btStateReceiver", "TURNING OFF");
			}
		}
	};

	private void dismissBtDialog()
	{
		if (btDialog != null)
		{
			Log.e(MainActivity.debuglog, "Closing bt dialog");
			btDialog.dismiss();
			btDialog = null;
		}
	}

	@Subscribe
	public void onMessageEvent(Events.NewGame newGame)
	{
		runOnUiThread(() -> newGame(newGame.requestState));
	}

	private void newGame(GameState gs)
	{
		Log.e("NEWGAME", "NEWGAME");
		closeGame();

		this.gs = gs;
		boardView.drawState(gs);

		if (this.gs.isBluetooth())
			setTitle("Bluetooth Game");
		else if (this.gs.isAi())
			setTitle("AI Game");
		else if (this.gs.isHuman()) //Normal local game
			setTitle("Human Game");
		else throw new IllegalStateException();

		if (!gs.isBluetooth())
		{
			setSubTitle(null);

			if (btService != null)
				btService.closeThread();
		}
		else if (btService != null)
			setSubTitle("Connected to " + btService.getConnectedDeviceName());

		dismissBtDialog();

		gameThread = new GameThread();
		gameThread.start();
	}

	@Subscribe
	public void onMessageEvent(Events.TurnLocal event)
	{
		runOnUiThread(this::turnLocal);
	}

	private void turnLocal()
	{
		if (!gs.isAi() && !gs.isHuman())
			newGame(GameState.builder().boards(gs.boards()).build());
	}

	private void newLocal()
	{
		newGame(GameState.builder().swapped(false).build());
	}

	private class GameThread extends Thread implements Closeable
	{
		private volatile boolean running;
		private Timer timer;

		@Override
		public void run()
		{
			Log.e("GAMETHREAD RAN", "yea");
			setName("GameThread");
			running = true;

			Source p1 = gs.players().first;
			Source p2 = gs.players().second;

			while (!gs.board().isDone() && running)
			{
				timer = new Timer(5000);

				if (gs.board().nextPlayer() == PLAYER && running)
					playAndUpdateBoard((p1 != Source.AI) ? getMove(p1) : gs.extraBot().move(gs.board(), timer));

				if (gs.board().isDone() || !running)
					continue;

				if (gs.board().nextPlayer() == ENEMY && running)
					playAndUpdateBoard((p2 != Source.AI) ? getMove(p2) : gs.extraBot().move(gs.board(), timer));
			}
		}

		@Override
		public void close() throws IOException
		{
			running = false;

			if (timer != null)
				timer.interrupt();

			interrupt();
		}
	}

	private void playAndUpdateBoard(Coord move)
	{
		if (move != null)
		{
			Board newBoard = gs.board().copy();
			newBoard.play(move);

			if (gs.players().contains(Source.Bluetooth)
					&& ((gs.board().nextPlayer() == PLAYER) == (gs.players().first == Source.Local)))
				btService.sendBoard(newBoard);

			gs.pushBoard(newBoard);
		}

		boardView.drawState(gs);
	}

	@Subscribe
	public void onMessageEvent(Events.NewMove moveEvent)
	{
		play(moveEvent.move, moveEvent.source);
	}

	private void play(Coord move, Source source)
	{
		synchronized (playerLock)
		{
			playerMove.set(new Pair<>(move, source));
			playerLock.notify();
		}
	}

	private Coord getMove(Source player)
	{
		playerMove.set(new Pair<>(null, null));
		while (!gs.board().availableMoves().contains(playerMove.get().first)    //Impossible move
				|| !player.equals(playerMove.get().second)                      //Wrong player
				|| playerMove.get() == null                                     //No Pair
				|| playerMove.get().first == null                               //No move
				|| playerMove.get().second == null)                             //No source
		{
			synchronized (playerLock)
			{
				try
				{
					playerLock.wait();
				}
				catch (InterruptedException e)
				{
					return null;
				}
			}
		}
		//TODO play sound
		return playerMove.getAndSet(null).first;
	}

	private void closeGame()
	{
		try
		{
			if (gameThread != null)
				gameThread.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	private void setTitle(String title)
	{
		final ActionBar actionBar = getSupportActionBar();

		if (actionBar != null)
			actionBar.setTitle(title);
	}

	private void setSubTitle(String subTitle)
	{
		final ActionBar actionBar = getSupportActionBar();

		if (actionBar != null)
			actionBar.setSubtitle(subTitle);
	}

	@Subscribe
	public void onMessageEvent(Events.Toast toastEvent)
	{
		runOnUiThread(() -> toast(toastEvent.text));
	}

	private void toast(String text)
	{
		if (toast == null)
			toast = Toast.makeText(MainActivity.this, "", Toast.LENGTH_SHORT);

		toast.setText(text);
		toast.show();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.menu, menu);
		return true;
	}

	@Subscribe
	public void onMessageEvent(Events.Undo undoEvent) //TODO not working
	{
		boolean forced = undoEvent.forced;

		if (forced)
			undo();
		else
		{
			runOnUiThread(() -> askUser(btService.getConnectedDeviceName() + " requests to undo the last move, do you accept?", allow ->
			{
				if (allow)
				{
					undo();
					btService.sendUndo(true);
				}
			}));
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item.getItemId() != R.id.action_undo)
			return false;

		undo();
		return true;
	}

	public void undo()
	{
		if (gs.boards().size() > 1)
		{
			GameState newState = GameState.builder().gs(gs).build();
			newState.popBoard();
			if (Source.AI == (gs.board().nextPlayer() == PLAYER ? gs.players().first : gs.players().second)
					&& newState.boards().size() > 1)
				newState.popBoard();

			newGame(newState);

			if (btService != null && btService.getState() == BtService.State.CONNECTED)
				btService.setLocalBoard(gs.board());
		}
		else toast("No previous moves");
	}

	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem item)
	{
		int id = item.getItemId();

		if (id == R.id.nav_local_human)
			DialogUtil.newLocal(accept -> newLocal(), this);
		else if (id == R.id.nav_local_ai)
			DialogUtil.newAi(this::newGame, this);
		else if (id == R.id.nav_bt_host)
			hostBt();
		else if (id == R.id.nav_bt_join)
			joinBt();
		else if (id == R.id.nav_other_feedback)
			DialogUtil.feedbackSender(this);
		else if (id == R.id.nav_other_share)
			DialogUtil.shareDialog(this);
		else if (id == R.id.nav_other_about)
			DialogUtil.aboutDialog(this);
		else return false;

		((DrawerLayout) findViewById(R.id.drawer_layout)).closeDrawer(GravityCompat.START);
		return true;
	}

	private void hostBt()
	{
		if (btAdapter == null)
		{
			Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
			return;
		}

		boolean discoverable = btAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;

		if (discoverable)
		{
			btService.listen();

			LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
			View layout = inflater.inflate(R.layout.dialog_bt_host, (ViewGroup) findViewById(R.id.bt_host_layout));
			((TextView) layout.findViewById(R.id.bt_host_desc)).setText(getString(R.string.host_desc, btService.getLocalBluetoothName()));

			RadioGroup.OnCheckedChangeListener onCheckedChangeListener = (group, checkedId) ->
			{
				//Get board type
				boolean newBoard = ((RadioButton) layout.findViewById(R.id.board_new)).isChecked();

				//Get the beginning player
				int beginner = ((RadioGroup) layout.findViewById(R.id.start_radio_group)).getCheckedRadioButtonId();
				boolean start = new Random().nextBoolean();
				if (beginner == R.id.start_you) start = true;
				else if (beginner == R.id.start_other) start = false;

				//Create the actual requested gamestate
				boolean swapped = newBoard ? (!start) : (start ^ (gs.board().nextPlayer() == Player.PLAYER));
				GameState.Builder gsBuilder = GameState.builder().bt().swapped(swapped);
				if (!newBoard)
					gsBuilder.board(gs.board());

				btService.setRequestState(gsBuilder.build());
			};

			((RadioGroup) layout.findViewById(R.id.start_radio_group)).setOnCheckedChangeListener(onCheckedChangeListener);
			((RadioGroup) layout.findViewById(R.id.board_radio_group)).setOnCheckedChangeListener(onCheckedChangeListener);

			btDialog = DialogUtil.keepDialog(new AlertDialog.Builder(this)
					.setView(layout)
					.setCustomTitle(DialogUtil.newTitle(this,Util.getString(this,R.string.host_bluetooth_game)))
					.setOnCancelListener(dialog -> btService.closeThread())
					.setNegativeButton("close", (dialog, which) ->
					{
						dismissBtDialog();
						btService.closeThread();
					}).show());
		}
		else
		{
			Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
			startActivityForResult(discoverableIntent, REQUEST_ENABLE_DSC);
		}
	}

	private void joinBt()
	{
		// If the adapter is null, then Bluetooth is not supported
		if (btAdapter == null)
		{
			Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
			return;
		}

		// If BT is not on, request that it be enabled first.
		if (!btAdapter.isEnabled())
		{
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
		}
		else
		{
			//If we don't have the COARSE LOCATION permission, request it
			if (PackageManager.PERMISSION_GRANTED != ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION))
				ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_COARSE_LOCATION);
			else btDialog = BtPicker.createDialog(this, btAdapter, adr -> btService.connect(adr));
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK)
			joinBt();
		else if (requestCode == REQUEST_ENABLE_DSC && resultCode != RESULT_CANCELED)
			hostBt();

		super.onActivityResult(requestCode, resultCode, data);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		if (requestCode == REQUEST_COARSE_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
			joinBt();

		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
	}

	private void askUser(String message, Callback<Boolean> callBack)
	{
		if (askDialog != null && askDialog.isShowing())
			askDialog.dismiss();

		DialogInterface.OnClickListener dialogClickListener = (dialog, which) ->
		{
			if (which == DialogInterface.BUTTON_POSITIVE)
				callBack.callback(true);
			else if (which == DialogInterface.BUTTON_NEGATIVE)
				callBack.callback(false);
		};

		askDialog = new AlertDialog.Builder(this).setMessage(message)
				.setPositiveButton("Yes", dialogClickListener)
				.setNegativeButton("No", dialogClickListener)
				.setOnDismissListener(dialogInterface -> callBack.callback(false))
				.show();

		DialogUtil.keepDialog(askDialog);
	}
}
