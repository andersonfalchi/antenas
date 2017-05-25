package ar.com.lichtmaier.antenas;

import android.Manifest;
import android.app.Activity;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.*;

import java.lang.ref.WeakReference;

public class LocationClientCompat implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener
{
	private final GoogleApiClient google;
	private final Activity activity;
	private final LocationRequest locationRequest;
	private final int REQUEST_CHECK_SETTINGS = 9988;
	private static boolean noPreguntar;
	private final Callback callback;
	private final MutableLiveData<Location> location = new MutableLiveData<>();

	private LocationClientCompat(FragmentActivity activity, LocationRequest locationRequest, Callback callback)
	{
		this.activity = activity;
		this.locationRequest = locationRequest;
		this.callback = callback;

		locationRequest.setMaxWaitTime(locationRequest.getInterval() * 6);
		google = new GoogleApiClient.Builder(activity)
			.addApi(LocationServices.API)
			.enableAutoManage(activity, this)
			.addConnectionCallbacks(this)
			.build();
	}

	@Nullable
	public static LocationClientCompat create(FragmentActivity activity, LocationRequest locationRequest, Callback callback)
	{
		int googlePlayServicesAvailable = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity);
		if(googlePlayServicesAvailable == ConnectionResult.SERVICE_MISSING || googlePlayServicesAvailable == ConnectionResult.SERVICE_INVALID)
		{
			callback.onConnectionFailed();
			return null;
		}
		return new LocationClientCompat(activity, locationRequest, callback);
	}

	public void start()
	{
		if(!google.isConnected())
		{
			google.connect();
			return;
		}
		try {
			LocationServices.FusedLocationApi.requestLocationUpdates(google, locationRequest, locationCallback, Looper.getMainLooper());
		} catch(SecurityException ignored) { }
	}

	@Override
	public void onConnected(Bundle bundle)
	{
		if(ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
				&& ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
			return;
		Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(google);
		if(lastLocation != null)
		{
			callback.onLocationChanged(lastLocation);
			location.setValue(lastLocation);
		}
		start();
		verificarConfiguración();
	}

	private void verificarConfiguración()
	{
		LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
			.addLocationRequest(locationRequest);
		PendingResult<LocationSettingsResult> result =
				LocationServices.SettingsApi.checkLocationSettings(google, builder.build());
		result.setResultCallback(result1 -> {
			Status status = result1.getStatus();
			if(status.getStatusCode() == LocationSettingsStatusCodes.RESOLUTION_REQUIRED && !noPreguntar /* && !activity.huboSavedInstanceState */)
			{
				try
				{
					status.startResolutionForResult(activity, REQUEST_CHECK_SETTINGS);
					noPreguntar = true;
				} catch(IntentSender.SendIntentException ignored)
				{
				}
			}
		});
	}

	public void stop()
	{
		if(google.isConnected())
		{
			LocationServices.FusedLocationApi.removeLocationUpdates(google, locationCallback);
			google.disconnect(); // No debería ser necesario... =/
		}
	}

	public void destroy()
	{
		google.unregisterConnectionCallbacks(this);
		google.unregisterConnectionFailedListener(this);
	}

	@Override
	public void onConnectionSuspended(int i)
	{
	}

	@Override
	public void onConnectionFailed(@NonNull ConnectionResult connectionResult)
	{
		callback.onConnectionFailed();
	}

	public boolean onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if(requestCode == REQUEST_CHECK_SETTINGS)
		{
			if(resultCode != Activity.RESULT_CANCELED)
				noPreguntar = false;
			Log.i("antenas", "resultCode=" + resultCode + " data="+ data);
			return true;
		}
		return false;
	}

	private static class MyLocationCallback extends LocationCallback
	{
		// because https://code.google.com/p/android/issues/detail?id=227856 =(
		final private WeakReference<LocationClientCompat> lcc;

		private MyLocationCallback(LocationClientCompat lcc)
		{
			this.lcc = new WeakReference<>(lcc);
		}

		@Override
		public void onLocationAvailability(LocationAvailability locationAvailability)
		{
			lcc.get().verificarConfiguración();
		}

		@Override
		public void onLocationResult(LocationResult result)
		{
			lcc.get().callback.onLocationChanged(result.getLastLocation());
			lcc.get().location.setValue(result.getLastLocation());
		}
	}
	final private LocationCallback locationCallback = new MyLocationCallback(this);

	interface Callback extends com.google.android.gms.location.LocationListener
	{
		void onConnectionFailed();
	}

	public LiveData<Location> getLocation()
	{
		return location;
	}
}
