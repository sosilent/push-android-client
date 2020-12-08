package com.unitrack.push.client.demo.service;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;

import com.unitrack.push.client.appuser.Message;
import com.unitrack.push.client.appuser.UDPClientBase;

import com.unitrack.push.client.demo.DateTimeUtil;
import com.unitrack.push.client.demo.MainActivity;
import com.unitrack.push.client.demo.Params;
import com.unitrack.push.client.demo.R;
import com.unitrack.push.client.demo.Util;
import com.unitrack.push.client.demo.receiver.TickAlarmReceiver;

import java.nio.ByteBuffer;

public class OnlineService extends Service {
	
	protected PendingIntent tickPendIntent;
	protected TickAlarmReceiver tickAlarmReceiver = new TickAlarmReceiver();
	WakeLock wakeLock;
	Client client;
	private String channelId = OnlineService.class.getName();

	public class Client extends UDPClientBase {

		public Client(int appId, byte[] uuid, String serverAddr, int serverPort)
				throws Exception {
			super(appId, uuid, serverAddr, serverPort);

		}

		@Override
		public boolean hasNetworkConnection() {
			return Util.hasNetwork(OnlineService.this);
		}
		

		@Override
		public void trySystemSleep() {
			tryReleaseWakeLock();
		}

		@Override
		public void onPushMessage(Message message) {
			if(message == null){
				return;
			}
			if(message.getData() == null || message.getData().length == 0){
				return;
			}
			if(message.getCmd() == 16){// 0x10 通用推送信息
				int no = message.getSerialNo();
				notifyUser(16,"Push通用推送信息: " + no,"时间："+DateTimeUtil.getCurDateTime(),"收到通用推送信息");
			}
			if(message.getCmd() == 17){// 0x11 分组推送信息
				long msg = ByteBuffer.wrap(message.getData(), Message.SERVER_MESSAGE_MIN_LENGTH, 8).getLong();
				int no = message.getSerialNo();
				notifyUser(17,"Push分组推送信息: " + no,""+msg,"收到通用推送信息");
			}
			if(message.getCmd() == 32){// 0x20 自定义推送信息
				String str = null;
				try{
					str = new String(message.getData(),Message.SERVER_MESSAGE_MIN_LENGTH, message.getContentLength(), "UTF-8");
				}catch(Exception e){
					str = Util.convert(message.getData(),Message.SERVER_MESSAGE_MIN_LENGTH, message.getContentLength());
				}
				int no = message.getSerialNo();
				notifyUser(32,"Push自定义推送信息:" + no,""+str,"收到自定义推送信息");
			}
			setPkgsInfo();
		}

	}

	public OnlineService() {
	}

	@SuppressLint("InvalidWakeLockTag")
	@Override
	public void onCreate() {
		super.onCreate();
		this.setTickAlarm();
		
		PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OnlineService");
		
		resetClient();
		
		notifyRunning();
	}

	@Override
	public int onStartCommand(Intent param, int flags, int startId) {
		if(param == null){
			return START_STICKY;
		}
		String cmd = param.getStringExtra("CMD");
		if(cmd == null){
			cmd = "";
		}
		if(cmd.equals("TICK")){
			if(wakeLock != null && wakeLock.isHeld() == false){
				wakeLock.acquire();
			}
		}
		if(cmd.equals("RESET")){
			if(wakeLock != null && wakeLock.isHeld() == false){
				wakeLock.acquire();
			}
			resetClient();
		}
		if(cmd.equals("TOAST")){
			String text = param.getStringExtra("TEXT");
			if(text != null && text.trim().length() != 0){
				Toast.makeText(this, text, Toast.LENGTH_LONG).show();
			}
		}
		
		setPkgsInfo();

		return START_STICKY;
	}
	
	protected void setPkgsInfo(){
		if(this.client == null){
			return;
		}
		long sent = client.getSentPackets();
		long received = client.getReceivedPackets();
		SharedPreferences account = this.getSharedPreferences(Params.DEFAULT_PRE_NAME,Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = account.edit();
		editor.putString(Params.SENT_PKGS, ""+sent);
		editor.putString(Params.RECEIVE_PKGS, ""+received);
		editor.commit();
	}
	
	protected void resetClient(){
		SharedPreferences account = this.getSharedPreferences(Params.DEFAULT_PRE_NAME,Context.MODE_PRIVATE);
		String serverIp = account.getString(Params.SERVER_IP, "");
		String serverPort = account.getString(Params.SERVER_PORT, "");
		String pushPort = account.getString(Params.PUSH_PORT, "");
		String userName = account.getString(Params.USER_NAME, "");
		if(serverIp == null || serverIp.trim().length() == 0
				|| serverPort == null || serverPort.trim().length() == 0
				|| pushPort == null || pushPort.trim().length() == 0
				|| userName == null || userName.trim().length() == 0){
			return;
		}
		if(this.client != null){
			try{
				client.stop();}catch(Exception e){}
		}
		try{
			int appId = 1;
			client = new Client(appId, Util.md5Byte(userName), serverIp, Integer.parseInt(serverPort));
			client.setHeartbeatInterval(50);
			client.start();
			SharedPreferences.Editor editor = account.edit();
			editor.putString(Params.SENT_PKGS, "0");
			editor.putString(Params.RECEIVE_PKGS, "0");
			editor.commit();
		}catch(Exception e){
			Toast.makeText(this.getApplicationContext(), "操作失败："+e.getMessage(), Toast.LENGTH_LONG).show();
		}
		Toast.makeText(this.getApplicationContext(), "ddpush：终端重置", Toast.LENGTH_LONG).show();
	}
	
	protected void tryReleaseWakeLock(){
		if(wakeLock != null && wakeLock.isHeld() == true){
			wakeLock.release();
		}
	}
	
	protected void setTickAlarm(){
		AlarmManager alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);  
		Intent intent = new Intent(this,TickAlarmReceiver.class);
		int requestCode = 0;  
		tickPendIntent = PendingIntent.getBroadcast(this,  
		requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);  
		//小米2s的MIUI操作系统，目前最短广播间隔为5分钟，少于5分钟的alarm会等到5分钟再触发！2014-04-28
		long triggerAtTime = System.currentTimeMillis();
		int interval = 300 * 1000;  
		alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP, triggerAtTime, interval, tickPendIntent);
	}
	
	protected void cancelTickAlarm(){
		AlarmManager alarmMgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		alarmMgr.cancel(tickPendIntent);  
	}
	
	protected void notifyRunning(){
		NotificationManager notificationManager=(NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);

		String channelId = "5996773";
		NotificationChannel channel = null;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			channel = new NotificationChannel(channelId, "安卓10a", NotificationManager.IMPORTANCE_DEFAULT);
			channel.enableLights(true);//是否在桌面icon右上角展示小红点
			channel.setLightColor(Color.GREEN);//小红点颜色
			channel.setShowBadge(false); //是否在久按桌面图标时显示此渠道的通知
			notificationManager.createNotificationChannel(channel);
		}

		Intent intent = new Intent(this,MainActivity.class);
		PendingIntent pi = PendingIntent.getActivity(this, 0,intent, PendingIntent.FLAG_ONE_SHOT);

		NotificationCompat.Builder notification = new NotificationCompat.Builder(this, channelId)
				.setContentTitle("Push")
				.setContentText("DDPushDemoUDP正在运行")
				.setContentIntent(pi)
				.setSmallIcon(R.drawable.ic_launcher)
				.setPriority(NotificationCompat.PRIORITY_HIGH)
				.setAutoCancel(true);

		notificationManager.notify(0, notification.build());
	}
	
	protected void cancelNotifyRunning(){
		NotificationManager notificationManager=(NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.cancel(0);
	}
	
	public void notifyUser(int id, String title, String content, String tickerText){
		NotificationManager notificationManager=(NotificationManager)this.getSystemService(Context.NOTIFICATION_SERVICE);


		NotificationChannel channel = null;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			channel = new NotificationChannel(channelId, "安卓10a", NotificationManager.IMPORTANCE_DEFAULT);
			channel.enableLights(true);//是否在桌面icon右上角展示小红点
			channel.setLightColor(Color.GREEN);//小红点颜色
			channel.setShowBadge(false); //是否在久按桌面图标时显示此渠道的通知
			notificationManager.createNotificationChannel(channel);
		}

		Intent intent = new Intent(this,MainActivity.class);
		PendingIntent pi = PendingIntent.getActivity(this, 0,intent, PendingIntent.FLAG_ONE_SHOT);

		NotificationCompat.Builder notification = new NotificationCompat.Builder(this, channelId)
				.setContentTitle(title)
				.setContentText(content)
				.setContentIntent(pi)
				.setSmallIcon(R.drawable.ic_launcher)
				.setPriority(NotificationCompat.PRIORITY_HIGH)
				.setAutoCancel(true);

		notificationManager.notify(0, notification.build());
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		//this.cancelTickAlarm();
		cancelNotifyRunning();
		this.tryReleaseWakeLock();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}


}
