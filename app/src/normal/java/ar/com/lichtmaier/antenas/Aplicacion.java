package ar.com.lichtmaier.antenas;

import android.app.Activity;
import android.app.Application;

import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.Tracker;

public class Aplicacion extends Application
{
	private Tracker tracker;

	private Tracker getTracker()
	{
		if(tracker == null)
		{
			GoogleAnalytics analytics = GoogleAnalytics.getInstance(this);
			if(Publicidad.crearAdRequestBuilder().build().isTestDevice(this))
				analytics.setDryRun(true);
			analytics.enableAutoActivityReports(this);
			tracker = analytics.newTracker(R.xml.analytics);
			tracker.enableAdvertisingIdCollection(true);
		}
		return tracker;
	}

	void reportActivityStart(Activity act)
	{
		getTracker();
		GoogleAnalytics.getInstance(this).reportActivityStart(act);
	}

	void reportActivityStop(Activity act)
	{
		GoogleAnalytics.getInstance(this).reportActivityStop(act);
	}
}
