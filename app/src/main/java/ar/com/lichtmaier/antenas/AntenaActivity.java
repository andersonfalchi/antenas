package ar.com.lichtmaier.antenas;

import java.lang.reflect.Field;
import java.text.NumberFormat;
import java.util.*;
import java.util.Map.Entry;

import org.gavaghan.geodesy.GlobalCoordinates;

import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.*;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationRequest;

public class AntenaActivity extends ActionBarActivity implements SensorEventListener, GooglePlayServicesClient.ConnectionCallbacks, GooglePlayServicesClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener
{
	private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
	public static final String PACKAGE = "ar.com.lichtmaier.antenas";

	final private Map<Antena, View> antenaAVista = new HashMap<>();
	final private Map<View, Antena> vistaAAntena = new HashMap<>();
	static GlobalCoordinates coordsUsuario;
	final private float[] gravity = new float[3];
	final private float[] geomagnetic = new float[3];
	private SensorManager sensorManager;
	private Sensor acelerómetro;
	private Sensor magnetómetro;
	private boolean hayInfoDeMagnetómetro = false, hayInfoDeAcelerómetro = false;
	private Publicidad publicidad;

	private LocationManager locationManager;
	private LocationRequest locationRequest;

	private final LocationListener locationListener = new LocationListener() {
		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) { }
		@Override
		public void onProviderEnabled(String provider) { }
		@Override
		public void onProviderDisabled(String provider) { }

		@Override
		public void onLocationChanged(Location location)
		{
			if(location.getAccuracy() > 300)
				return;
			coordsUsuario = new GlobalCoordinates(location.getLatitude(), location.getLongitude());
			nuevaUbicación();
		}
	};

	static FlechaView flechaADesaparecer;
	private View.OnClickListener onAntenaClickedListener = new View.OnClickListener()
	{
		@Override
		public void onClick(View v)
		{
			Intent i = new Intent(AntenaActivity.this, UnaAntenaActivity.class);
			Antena antena = vistaAAntena.get(v);

			int[] screenLocation = new int[2];
			FlechaView flecha = (FlechaView)v.findViewById(R.id.flecha);
			flecha.getLocationOnScreen(screenLocation);
			int orientation = getResources().getConfiguration().orientation;
			i.putExtra(PACKAGE + ".antena", antena.index).
					putExtra(PACKAGE + ".orientation", orientation).
					putExtra(PACKAGE + ".left", screenLocation[0]).
					putExtra(PACKAGE + ".top", screenLocation[1]).
					putExtra(PACKAGE + ".width", flecha.getWidth()).
					putExtra(PACKAGE + ".height", flecha.getHeight()).
					putExtra(PACKAGE + ".ángulo", flecha.getÁngulo());
			flechaADesaparecer = flecha;

			startActivity(i);
			overridePendingTransition(0, 0);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		asignarLayout();

		try
		{
			Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
			if(menuKeyField != null)
			{
				menuKeyField.setAccessible(true);
				menuKeyField.setBoolean(ViewConfiguration.get(this), false);
			}
		} catch(Exception e) { }

		ContentLoadingProgressBar pb = (ContentLoadingProgressBar)findViewById(R.id.progressBar);
		if(pb != null)
			pb.show();

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
			@Override
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
			{
				nuevaUbicación();
			}
		});

		sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
		magnetómetro = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		acelerómetro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

		locationClient = new LocationClient(this, this, this);
		locationRequest = LocationRequest.create();
		locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
		locationRequest.setInterval(10000);
		locationRequest.setFastestInterval(2000);

		if(savedInstanceState != null && savedInstanceState.containsKey("lat"))
		{
			coordsUsuario = new GlobalCoordinates(savedInstanceState.getDouble("lat"), savedInstanceState.getDouble("lon"));
			nuevaUbicación();
		}

		publicidad = new Publicidad(this);
	}

	protected void asignarLayout()
	{
		setContentView(R.layout.activity_antena);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		super.onSaveInstanceState(outState);
		if(coordsUsuario != null)
		{
			outState.putDouble("lat", coordsUsuario.getLatitude());
			outState.putDouble("lon", coordsUsuario.getLongitude());
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.antena, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		int id = item.getItemId();
		Intent i;
		switch(id)
		{
			case R.id.action_settings:
				i = new Intent(this, PreferenciasActivity.class);
				startActivity(i);
				return true;
			case R.id.action_mapa:
				i = new Intent(this, MapaActivity.class);
				startActivity(i);
				return true;
			case R.id.action_ayuda:
				i = new Intent(Intent.ACTION_VIEW, Uri.parse("http://poné-tda.com.ar/"));
				if(i.resolveActivity(getPackageManager()) != null)
					startActivity(i);
				return true;
			case R.id.action_niqueco:
				i = new Intent(Intent.ACTION_VIEW, Uri.parse("http://twitter.com/niqueco"));
				PackageManager pm = getPackageManager();
				List<ResolveInfo> l = pm.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY);
				for(ResolveInfo info : l)
					if(info.activityInfo.packageName.equals("com.twitter.android"))
					{
						i.setPackage(info.activityInfo.packageName);
						break;
					}
				startActivity(i);
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onStart()
	{
		super.onStart();
		((Aplicacion)getApplication()).reportActivityStart(this);
		if(locationClient != null)
			locationClient.connect();
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		sensorManager.registerListener(this, magnetómetro, SensorManager.SENSOR_DELAY_UI);
		sensorManager.registerListener(this, acelerómetro, SensorManager.SENSOR_DELAY_UI);
		if(locationManager != null)
		{
			Criteria criteria = new Criteria();
			criteria.setAccuracy(Criteria.ACCURACY_COARSE);
			criteria.setCostAllowed(true);
			try
			{
				Compat.requestLocationUpdates(locationManager, 1000 * 60, 0, criteria, locationListener);
			} catch(IllegalArgumentException e)
			{
				Log.e("antenas", "Error pidiendo updates de GPS", e);
				Toast.makeText(this, getString(R.string.no_ubicacion), Toast.LENGTH_SHORT).show();
				finish();
			}
		}
		if(locationClient != null)
		{
			if(locationClient.isConnected())
				locationClient.requestLocationUpdates(locationRequest, this);
			else if(!locationClient.isConnecting())
				locationClient.connect();
		}
		publicidad.onResume();
	}

	@Override
	protected void onPause()
	{
		publicidad.onPause();
		hayInfoDeAcelerómetro = false;
		hayInfoDeMagnetómetro = false;
		sensorManager.unregisterListener(this);
		if(locationManager != null)
			locationManager.removeUpdates(locationListener);
		if(locationClient != null && locationClient.isConnected())
			locationClient.removeLocationUpdates(this);
		super.onPause();
	}

	@Override
	protected void onStop()
	{
		if(locationClient != null)
			locationClient.disconnect();
		((Aplicacion)getApplication()).reportActivityStop(this);
		super.onStop();
	}

	@Override
	protected void onDestroy()
	{
		publicidad.onDestroy();
		super.onDestroy();
	}

	private LocationClient locationClient;
	private SharedPreferences prefs;
	void nuevaOrientación(double brújula)
	{
		//NumberFormat nf = NumberFormat.getInstance(new Locale("es", "AR"));
		//((TextView)findViewById(R.id.orientacion)).setText(nf.format(brújula) /*+ " " + nf.format(Math.PI/2.0 - brújula)*/);
		//Log.d("antenas", "orientacion: " + values[0]);
		for(Entry<Antena, View> e : antenaAVista.entrySet())
		{
			Antena antena = e.getKey();
			double rumbo = antena.rumboDesde(coordsUsuario);
			FlechaView f = (FlechaView)e.getValue().findViewById(R.id.flecha);
			f.setÁngulo(rumbo - brújula);
		}
	}

	final private float[] r = new float[9];
	final private float[] values = new float[3];
	final private float[] r2 = new float[9];

	@Override
	public void onSensorChanged(SensorEvent event)
	{
		if(event.sensor == magnetómetro)
		{
			System.arraycopy(event.values, 0, geomagnetic, 0, 3);
			hayInfoDeMagnetómetro = true;
		} else if(event.sensor == acelerómetro)
		{
			System.arraycopy(event.values, 0, gravity, 0, 3);
			hayInfoDeAcelerómetro = true;
		}
		if(hayInfoDeAcelerómetro && hayInfoDeMagnetómetro)
		{
			SensorManager.getRotationMatrix(r, null, gravity, geomagnetic);
			@SuppressWarnings("deprecation")
			int rotation = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getOrientation();
			int axisX;
			int axisY;
			switch(rotation)
			{
				case Surface.ROTATION_0:
					axisX = SensorManager.AXIS_X;
					axisY = SensorManager.AXIS_Y;
					break;
				case Surface.ROTATION_90:
					axisX = SensorManager.AXIS_Y;
					axisY = SensorManager.AXIS_MINUS_X;
					break;
				case Surface.ROTATION_180:
					axisX = SensorManager.AXIS_MINUS_X;
					axisY = SensorManager.AXIS_MINUS_Y;
					break;
				case Surface.ROTATION_270:
					axisX = SensorManager.AXIS_MINUS_Y;
					axisY = SensorManager.AXIS_X;
					break;
				default:
					throw new RuntimeException("rot: " + rotation);
			}
			SensorManager.remapCoordinateSystem(r, axisX, axisY, r2);
			SensorManager.getOrientation(r2, values);
			double brújula = Math.toDegrees(values[0]);
			if(brújula < 0)
				brújula += 360;
			nuevaOrientación(brújula);
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) { }

	protected void nuevaUbicación()
	{
		int maxDist = Integer.parseInt(prefs.getString("max_dist", "60")) * 1000;
		List<Antena> antenasCerca = Antena.dameAntenasCerca(this, coordsUsuario,
				maxDist,
				prefs.getBoolean("menos", true));
		Iterator<Entry<Antena, View>> it = antenaAVista.entrySet().iterator();
		ViewGroup contenedor = (ViewGroup)findViewById(R.id.antenas);
		while(it.hasNext())
		{
			Entry<Antena, View> e = it.next();
			if(!antenasCerca.contains(e.getKey()))
			{
				contenedor.removeView(e.getValue());
				vistaAAntena.remove(e.getValue());
				it.remove();
			} else
			{
				ponéDistancia(e.getKey(), e.getValue());
			}
		}
		LayoutInflater inf = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
		for(Antena a : antenasCerca)
		{
			if(!antenaAVista.containsKey(a))
			{
				View v = inf.inflate(R.layout.antena, contenedor, false);
				int n = contenedor.getChildCount(), i;
				for(i = 0 ; i < n ; i++)
				{
					View child = contenedor.getChildAt(i);
					if(vistaAAntena.get(child).dist > a.dist)
					{
						contenedor.addView(v, i);
						break;
					}
				}
				if(i == n)
					contenedor.addView(v);
				v.setOnClickListener(onAntenaClickedListener);
				v.setFocusable(true);
				((TextView)v.findViewById(R.id.antena_desc)).setText(a.toString());
				ponéDistancia(a, v);
				antenaAVista.put(a, v);
				vistaAAntena.put(v, a);
			}
		}
		ContentLoadingProgressBar pb = (ContentLoadingProgressBar)findViewById(R.id.progressBar);
		pb.hide();
		TextView problema = (TextView)findViewById(R.id.problema);
		if(antenasCerca.isEmpty())
		{
			StringBuilder sb = new StringBuilder(getString(R.string.no_se_encontraron_antenas, formatDistance(maxDist)));
			String[] vv = getResources().getStringArray(R.array.pref_max_dist_values);
			if(Integer.parseInt(vv[vv.length-1]) * 1000 != maxDist)
				sb.append(' ').append(getString(R.string.podes_incrementar_radio));
			problema.setText(sb.toString());
			problema.setVisibility(View.VISIBLE);
		} else
		{
			problema.setVisibility(View.GONE);
		}
	}

	private void ponéDistancia(Antena a, View v)
	{
		ponéDistancia(a, (TextView) v.findViewById(R.id.antena_dist));
	}

	protected void ponéDistancia(Antena a, TextView tv)
	{
		tv.setText(formatDistance(a.distanceTo(coordsUsuario)));
	}

	final static private NumberFormat nf = NumberFormat.getNumberInstance(new Locale("es", "AR"));
	private static String formatDistance(double distancia)
	{
		nf.setMaximumFractionDigits(distancia < 1000.0 ? 2 : 1);
		return nf.format(distancia / 1000.0) + " km";
	}

	@Override
	public void onConnectionFailed(ConnectionResult r)
	{
		if(r.hasResolution())
		{
			Log.w("antenas", "Play Services: " + r);
			try
			{
				r.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
			} catch(SendIntentException e)
			{
				e.printStackTrace();
			}
		} else
		{
			Log.e("antenas", "Play Services no disponible: " + r + ". No importa, sobreviviremos.");
			locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
			locationClient = null;
			if(coordsUsuario == null)
			{
				Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
				if(location != null && location.getAccuracy() < 300)
				{
					coordsUsuario = new GlobalCoordinates(location.getLatitude(), location.getLongitude());
					nuevaUbicación();
				} else
				{
					locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
					if(location != null && location.getAccuracy() < 300)
					{
						coordsUsuario = new GlobalCoordinates(location.getLatitude(), location.getLongitude());
						nuevaUbicación();
					}
				}
			}
		}
	}

	@Override
	public void onConnected(Bundle arg0)
	{
		Location location = locationClient.getLastLocation();
		if(location != null)
		{
			coordsUsuario = new GlobalCoordinates(location.getLatitude(), location.getLongitude());
			nuevaUbicación();
		}
		locationClient.requestLocationUpdates(locationRequest, this);
		publicidad.load(location);
	}

	@Override
	public void onDisconnected()
	{
	}

	@Override
	public void onLocationChanged(Location location)
	{
		coordsUsuario = new GlobalCoordinates(location.getLatitude(), location.getLongitude());
		nuevaUbicación();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch(requestCode)
		{
			case CONNECTION_FAILURE_RESOLUTION_REQUEST:
				if(resultCode == RESULT_OK)
				{
					locationClient.connect();
				}
		}
	}
}