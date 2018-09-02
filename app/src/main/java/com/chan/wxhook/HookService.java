package com.chan.wxhook;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HookService extends AccessibilityService {
	public static final String HOOK_READ_CONTACT_ACTION = "com.chan.wxhook.contact";
	public static final String HOOK_SYNC_WX_ACTION = "com.chan.wxhook.syncData";

	private static final String EXCEL_XLS = "xls";
	private static final String EXCEL_XLSX = "xlsx";

	public static final String EXTRA_CONTACTS = "contacts";

	private static final String TAG = "HookService";
	private Handler mHandler;
	private Info mCurrentInfo = new Info();
	private List<String> mContacts = new ArrayList<>();
	private HookBroadcast mHookBroadcast = new HookBroadcast();
	private Map<String, Info> mMap = new HashMap<>();

	@Override
	public void onCreate() {
		super.onCreate();
		mHandler = new Handler();
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(HOOK_READ_CONTACT_ACTION);
		intentFilter.addAction(HOOK_SYNC_WX_ACTION);
		registerReceiver(mHookBroadcast, intentFilter);
	}

	@Override
	public void onDestroy() {
		unregisterReceiver(mHookBroadcast);
		super.onDestroy();
	}

	@Override
	public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
		CharSequence pageName = accessibilityEvent.getClassName();
		Log.d(TAG, "enter " + pageName);

		if (TextUtils.equals("com.tencent.mm.ui.LauncherUI", pageName)) {
			handleLauncherUI();
		} else if (TextUtils.equals("com.tencent.mm.plugin.subapp.ui.friend.FMessageConversationUI", pageName)) {
			handleFMessageConversationUI();
		} else if (TextUtils.equals("com.tencent.mm.plugin.fts.ui.FTSAddFriendUI", pageName) ||
				TextUtils.equals("com.tencent.mm.ui.base.p", pageName)) {
			handleFTSAddFriendUI();
		} else if (TextUtils.equals("com.tencent.mm.plugin.profile.ui.ContactInfoUI", pageName)) {
			handleSearchSuccess();
		} else if (TextUtils.equals("com.tencent.mm.plugin.profile.ui.ContactMoreInfoUI", pageName)) {
			handleContactMoreInfo();
		}
	}

	/**
	 * 微信首页
	 */
	private void handleLauncherUI() {
		// 首页
		Log.d(TAG, "handleLauncherUI");
		if (mContacts.isEmpty()) {
			return;
		}

		AccessibilityNodeInfo root = getRootInActiveWindow();
		if (root == null) {
			Log.d(TAG, "fetch node info failed");
			return;
		}

		clickedNodeByText(root, "通讯录");
		clickedNodeByText(root, "新的朋友", 2000, true);
	}

	/**
	 * 进入搜索好友
	 */
	private void handleFMessageConversationUI() {
		Log.d(TAG, "handleFMessageConversationUI");
		if (mContacts.isEmpty()) {
			return;
		}

		AccessibilityNodeInfo root = getRootInActiveWindow();
		if (root == null) {
			Log.d(TAG, "fetch node info failed");
			return;
		}
		clickedNodeById(root, "com.tencent.mm:id/bf2");
		root.recycle();
	}

	/**
	 * 搜索好友页面
	 */
	private void handleFTSAddFriendUI() {
		Log.d(TAG, "handleFTSAddFriendUI");
		if (mContacts.isEmpty()) {
			syncData();
			Toast.makeText(this, "finished", Toast.LENGTH_SHORT).show();
			Log.d(TAG, "mContacts is empty");
			return;
		}

		AccessibilityNodeInfo root = getRootInActiveWindow();
		AccessibilityNodeInfo inputNode = findNodeById(root, "com.tencent.mm:id/jd");
		if (inputNode == null) {
			root.recycle();
			return;
		}

		// 不断的从第一个联系人开始搜索
		mCurrentInfo = new Info();
		mCurrentInfo.contacts = mContacts.get(0);
		mContacts.remove(0);
		Bundle arguments = new Bundle();
		arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, mCurrentInfo.contacts);
		arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD);
		arguments.putBoolean(AccessibilityNodeInfo.ACTION_ARGUMENT_EXTEND_SELECTION_BOOLEAN, true);
		inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
		inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
		inputNode.performAction(AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY, arguments);

		mMap.put(mCurrentInfo.contacts, mCurrentInfo);
		Log.d(TAG, "current -> " + mCurrentInfo.contacts);
		String targetText = "搜索:" + mCurrentInfo.contacts;
		clickedNodeByText(root, targetText, 200, true);
	}

	/**
	 * 搜索成功
	 */
	private void handleSearchSuccess() {
		Log.d(TAG, "handleSearchSuccess");
		if (mCurrentInfo == null) {
			Log.d(TAG, "mCurrentInfo is null");
			performGlobalAction(GLOBAL_ACTION_BACK);
			return;
		}

		AccessibilityNodeInfo root = getRootInActiveWindow();
		AccessibilityNodeInfo nickname = findNodeById(root, "com.tencent.mm:id/art");
		if (nickname != null && !TextUtils.isEmpty(nickname.getText())) {
			mCurrentInfo.nickname = String.valueOf(nickname.getText()).replace("微信:", "");
			Log.d(TAG, "nickname: " + mCurrentInfo.nickname);
		}

		AccessibilityNodeInfo gender = findNodeById(root, "com.tencent.mm:id/as4");
		if (gender != null) {
			mCurrentInfo.gender = String.valueOf(gender.getContentDescription());
			Log.d(TAG, "gender: " + mCurrentInfo.gender);
		}


		AccessibilityNodeInfo node = findNodeByText(root, "个性签名");
		if (node != null) {
			AccessibilityNodeInfo extraInfo = findNodeById(root, "android:id/summary");
			if (extraInfo != null) {
				mCurrentInfo.extraInfo = String.valueOf(extraInfo.getText());
				Log.d(TAG, "extraInfo: " + mCurrentInfo.extraInfo);
			}
		}

		Log.d(TAG, "click more button");
		// 更多按钮
		AccessibilityNodeInfo more = findNodeById(root, "com.tencent.mm:id/ci");
		if (more == null || !TextUtils.isEmpty(mCurrentInfo.extraInfo)) {
			mCurrentInfo = null;
			performGlobalAction(GLOBAL_ACTION_BACK);
			root.recycle();
			return;
		}

		clickedNodeById(root, "com.tencent.mm:id/ci");
		root.recycle();
	}

	/**
	 * 搜索成功后点击更多按钮
	 */
	private void handleContactMoreInfo() {
		Log.d(TAG, "handleContactMoreInfo");
		if (mCurrentInfo == null) {
			Log.d(TAG, "mCurrentInfo is null");
			performGlobalAction(GLOBAL_ACTION_BACK);
			return;
		}

		AccessibilityNodeInfo root = getRootInActiveWindow();
		AccessibilityNodeInfo extraInfo = findNodeByText(root, "个性签名");
		if (extraInfo != null) {
			if (extraInfo.getParent() != null &&
					(extraInfo = findNodeById(extraInfo.getParent(), "com.tencent.mm:id/cxa")) != null) {
				mCurrentInfo.extraInfo = String.valueOf(extraInfo.getText());
				Log.d(TAG, "extraInfo: " + mCurrentInfo.extraInfo);
			}
		}

		mCurrentInfo = null;
		performGlobalAction(GLOBAL_ACTION_BACK);
	}

	private void syncData() {
		Log.d(TAG, "syncData");
		saveExcelFile();
		Log.d(TAG, "syncData success");
	}

	@Override
	public void onInterrupt() {
		Log.d(TAG, "onInterrupt");
	}

	private void clickedNodeById(AccessibilityNodeInfo root, String id) {
		AccessibilityNodeInfo target = findNodeById(root, id);
		if (target == null) {
			Log.d(TAG, "find node by id " + id + " failed");
			return;
		}

		while (target != null && !target.isClickable()) {
			target = target.getParent();
		}

		if (target != null) {
			target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
		}
	}

	private AccessibilityNodeInfo findNodeById(AccessibilityNodeInfo root, String id) {
		List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByViewId(id);
		if (list == null || list.isEmpty()) {
			return null;
		}
		return list.get(list.size() - 1);
	}

	private void clickedNodeByText(AccessibilityNodeInfo root, String text) {
		clickedNodeByText(root, text, 0, false);
	}

	private void clickedNodeByText(final AccessibilityNodeInfo root, final String text, long delay, final boolean recycle) {
		if (delay <= 0) {
			doClickedNodeByText(root, text);
			if (recycle) {
				root.recycle();
			}
			return;
		}

		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				doClickedNodeByText(root, text);
				if (recycle) {
					root.recycle();
				}
			}
		}, delay);
	}

	private void doClickedNodeByText(AccessibilityNodeInfo root, String text) {
		AccessibilityNodeInfo target = findNodeByText(root, text);
		if (target == null) {
			Log.d(TAG, "find node by text " + text + " failed");
			return;
		}
		while (target != null && !target.isClickable()) {
			target = target.getParent();
		}

		if (target != null) {
			target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
		}
	}

	private AccessibilityNodeInfo findNodeByText(AccessibilityNodeInfo root, String text) {
		List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(text);
		if (list == null || list.isEmpty()) {
			return null;
		}
		return list.get(list.size() - 1);
	}


	public class HookBroadcast extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent == null) {
				return;
			}

			String action = intent.getAction();
			if (TextUtils.equals(action, HOOK_SYNC_WX_ACTION)) {
				syncData();
				Toast.makeText(HookService.this, "同步成功", Toast.LENGTH_SHORT).show();
				return;
			}

			String json = intent.getStringExtra("contacts");
			Log.d(TAG, json);
			if (!TextUtils.isEmpty(json)) {
				mContacts = new Gson().fromJson(json, new TypeToken<List<String>>() {
				}.getType());
			}

			Log.d(TAG, "receive contacts, size: " + mContacts.size());
			Toast.makeText(HookService.this, "打开成功", Toast.LENGTH_SHORT).show();
		}
	}

	private void saveExcelFile() {

		//New Workbook
		Workbook wb = new HSSFWorkbook();

		//New Sheet
		Sheet sheet = wb.createSheet("myOrder");

		// Generate column headings
		Row row = sheet.createRow(0);
		Cell c = row.createCell(0);
		c.setCellValue("联系人");

		c = row.createCell(1);
		c.setCellValue("昵称");

		c = row.createCell(2);
		c.setCellValue("个性签名");

		c = row.createCell(3);
		c.setCellValue("性别");

		List<Info> dataList = new ArrayList<>(mMap.values());
		for (int j = 0; j < dataList.size(); j++) {
			// 创建一行：从第二行开始，跳过属性列
			row = sheet.createRow(j + 1);
			// 得到要插入的每一条记录
			Info info = dataList.get(j);
			for (int k = 0; k <= 4; k++) {
				// 在一行内循环
				Cell cell = row.createCell(0);
				cell.setCellValue(info.contacts);

				cell = row.createCell(1);
				cell.setCellValue(info.nickname);

				cell = row.createCell(2);
				cell.setCellValue(info.extraInfo);

				cell = row.createCell(3);
				cell.setCellValue(info.gender);
			}
		}

		File file = new File(Environment.getExternalStorageDirectory(), "output.xls");
		// Create a path where we will place our List of objects on external storage
		FileOutputStream os = null;

		try {
			os = new FileOutputStream(file);
			wb.write(os);
			Log.d(TAG, "Writing file" + file);
		} catch (IOException e) {
			Log.d(TAG, "Error writing " + file, e);
		} catch (Exception e) {
			Log.d(TAG, "Failed to save file", e);
		} finally {
			try {
				if (null != os)
					os.close();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}
}
