package com.unitrack.push.client.demo.udp.receiver;

import com.unitrack.push.client.demo.udp.Util;
import com.unitrack.push.client.demo.udp.service.OnlineService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ConnectivityAlarmReceiver extends BroadcastReceiver {

	public ConnectivityAlarmReceiver() {
		super();
	}

	@Override
	public void onReceive(Context context, Intent intent) {

		if(Util.hasNetwork(context) == false){
			return;
		}
		Intent startSrv = new Intent(context, OnlineService.class);
		startSrv.putExtra("CMD", "RESET");
		context.startService(startSrv);
	}

}
