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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HookService extends AccessibilityService {
	public static final String HOOK_ACTION = "com.chan.wxhook.fuck";
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
		intentFilter.addAction(HOOK_ACTION);
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
		} else if (TextUtils.equals("com.tencent.mm.plugin.fts.ui.FTSAddFriendUI", pageName)) {
			handleFTSAddFriendUI();
		} else if (TextUtils.equals("com.tencent.mm.plugin.profile.ui.ContactInfoUI", pageName)) {
			handleSearchSuccess();
		} else if (TextUtils.equals("com.tencent.mm.plugin.profile.ui.ContactMoreInfoUI", pageName)) {
			handleContactMoreInfo();
		}
	}

	/**
	 * 微信首页
	 * */
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
	 * */
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
	 * */
	private void handleFTSAddFriendUI() {
		Log.d(TAG, "handleFTSAddFriendUI");
		if (mContacts.isEmpty()) {
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

		String targetText = "搜索:" + mCurrentInfo.contacts;
		AccessibilityNodeInfo searchNode = findNodeByText(root, targetText);
		if (searchNode == null) {
			handleEnd();
			root.recycle();
			return;
		}

		clickedNodeByText(root, targetText, 200, true);
	}

	/**
	 * 搜索成功
	 * */
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
		}

		AccessibilityNodeInfo gender = findNodeById(root, "com.tencent.mm:id/as4");
		if (gender != null) {
			mCurrentInfo.gender = String.valueOf(gender.getContentDescription());
		}

		AccessibilityNodeInfo extraInfo = findNodeById(root, "android:id/summary");
		if (extraInfo != null) {
			mCurrentInfo.extraInfo = String.valueOf(extraInfo.getText());
		}

		AccessibilityNodeInfo more = findNodeById(root, "com.tencent.mm:id/ci");
		if (more == null || !TextUtils.isEmpty(mCurrentInfo.extraInfo)) {
			handleEnd();
			performGlobalAction(GLOBAL_ACTION_BACK);
			root.recycle();
			return;
		}

		clickedNodeById(root, "com.tencent.mm:id/ci");
		root.recycle();
	}

	/**
	 * 搜索成功后点击更多按钮
	 * */
	private void handleContactMoreInfo() {
		Log.d(TAG, "handleContactMoreInfo");
		if (mCurrentInfo == null) {
			Log.d(TAG, "mCurrentInfo is null");
			performGlobalAction(GLOBAL_ACTION_BACK);
			return;
		}

		AccessibilityNodeInfo root = getRootInActiveWindow();
		AccessibilityNodeInfo extraInfo = findNodeById(root, "com.tencent.mm:id/cxa");
		if (extraInfo != null) {
			mCurrentInfo.extraInfo = String.valueOf(extraInfo.getText());
			handleEnd();
			performGlobalAction(GLOBAL_ACTION_BACK);
		}
	}

	private void handleEnd() {
		Log.d(TAG, "handleEnd");

		if (mCurrentInfo != null) {
			mMap.put(mCurrentInfo.contacts, mCurrentInfo);
		}

		mCurrentInfo = null;
		if (!mContacts.isEmpty()) {
			Log.d(TAG, "has more contacts");
			return;
		}

		String json = new Gson().toJson(mMap);
		File file = new File(Environment.getExternalStorageDirectory(), "output.txt");
		BufferedWriter bufferedWriter = null;
		try {
			bufferedWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
			bufferedWriter.write(json);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (bufferedWriter != null) {
					bufferedWriter.flush();
					bufferedWriter.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
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

			String json = intent.getStringExtra("contacts");
			Log.d(TAG, json);
			if (!TextUtils.isEmpty(json)) {
				mContacts = new Gson().fromJson(json, new TypeToken<List<String>>() {
				}.getType());
			}

			Log.d(TAG, "receive contacts, size: " + mContacts.size());
		}
	}
}
