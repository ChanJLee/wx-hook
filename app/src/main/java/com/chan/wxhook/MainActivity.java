package com.chan.wxhook;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

	private static final String PERMISSION_LIST[] = {
			Manifest.permission.READ_CONTACTS,
			Manifest.permission.WRITE_EXTERNAL_STORAGE,
	};

	private static final int PERMISSION_REQUEST_CODE = 0x0525;
	private static final String TAG = "chan_debug";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		findViewById(R.id.fuck).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				doFuck();
			}
		});
	}

	private void doFuck() {
		if (!checkPermissions()) {
			return;
		}

		if (!checkAccessibilityEnable()) {
			return;
		}

		Toast.makeText(this, "服务打开成功", Toast.LENGTH_SHORT).show();
		fetchContacts();
	}

	private boolean checkAccessibilityEnable() {
		int accessibilityEnable = 0;
		String serviceName = getPackageName() + "/" + HookService.class.getCanonicalName();
		try {
			accessibilityEnable = Settings.Secure.getInt(getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);
		} catch (Exception e) {
			Log.e(TAG, "get accessibility enable failed, the err:" + e.getMessage());
		}

		if (accessibilityEnable == 1) {
			TextUtils.SimpleStringSplitter simpleStringSplitter = new TextUtils.SimpleStringSplitter(':');
			String settingValue = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
			if (settingValue != null) {
				simpleStringSplitter.setString(settingValue);
				while (simpleStringSplitter.hasNext()) {
					String accessibilityService = simpleStringSplitter.next();
					if (accessibilityService.equalsIgnoreCase(serviceName)) {
						Log.v(TAG, "We've found the correct setting - accessibility is switched on!");
						return true;
					}
				}
			}
		} else {
			Log.d(TAG, "Accessibility service disable");
		}

		Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
		startActivity(intent);

		return false;
	}

	private boolean checkPermissions() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
			return true;
		}

		for (String permission : PERMISSION_LIST) {
			if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
				requestPermissions(PERMISSION_LIST, PERMISSION_REQUEST_CODE);
				return false;
			}
		}

		return true;
	}

	private void fetchContacts() {
		ContentResolver contentResolver = getContentResolver();
		if (contentResolver == null) {
			Toast.makeText(this, "content resolver is null", Toast.LENGTH_SHORT).show();
			return;
		}

		//联系人的Uri，也就是content://com.android.contacts/contacts
		Uri uri = ContactsContract.Contacts.CONTENT_URI;

		//指定获取_id和display_name两列数据，display_name即为姓名
		String[] projection = new String[]{
				ContactsContract.Contacts._ID,
				ContactsContract.Contacts.DISPLAY_NAME
		};

		//根据Uri查询相应的ContentProvider，cursor为获取到的数据集
		Cursor cursor = contentResolver.query(uri, projection, null, null, null);
		try {
			if (cursor != null && cursor.moveToFirst()) {
				do {
					Long id = cursor.getLong(0);

					//获取姓名
					String name = cursor.getString(1);

					//指定获取NUMBER这一列数据
					String[] phoneProjection = new String[]{
							ContactsContract.CommonDataKinds.Phone.NUMBER
					};

					//根据联系人的ID获取此人的电话号码
					Cursor phonesCursor = this.getContentResolver().query(
							ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
							phoneProjection,
							ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=" + id,
							null,
							null);

					//因为每个联系人可能有多个电话号码，所以需要遍历
					if (phonesCursor != null && phonesCursor.moveToFirst()) {
						do {
							String num = phonesCursor.getString(0);
							Log.d(TAG, id + " " + name + " " + num);
						} while (phonesCursor.moveToNext());
					}
				} while (cursor.moveToNext());
			}
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		for (int resultCode : grantResults) {
			if (resultCode != PackageManager.PERMISSION_GRANTED) {
				Toast.makeText(this, "权限申请失败，请手动打开权限", Toast.LENGTH_SHORT).show();
				return;
			}
		}
	}
}
